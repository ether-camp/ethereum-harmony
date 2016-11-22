/**
 * Allows to view contract values in blockchain storage.
 */

(function() {
    'use strict';

    var PAGE_SIZE = 30;
    var NULL = '<empty>';

    function isBytesAreString(data) {
        var isString = false;

        // check leading non zero characters
        if (/^[^(00)][0-9a-f]{62}/gi.test(data)) {
            // check trailing zero characters with string length byte
            isString = /(00)+[0-9a-f]{2}?$/gi.test(data);

            // check if all characters (except last byte) are printable ascii
            if (!isString) {
                // remove trailing zeroes and string length (if exists).
                var bytes = data.replace(/(00)*[0-9a-f]{2}?$/gi, '').match(/.{2}/g);
                isString = !_.find(bytes, function (byte) {
                    return !isPrintableAscii(byte);
                });
            }
        }

        return isString;
    }

    function isPrintableAscii(hexCode) {
        (typeof hexCode == 'string') && (hexCode = parseInt(hexCode, 16));
        return (hexCode >= 32) && (hexCode < 127);
    }

    function bytesToString(data) {
        var result = '';
        var bytes = data.replace(/(00)*(00[0-9a-f]{2})?$/gi, '').match(/.{2}/g);
        bytes.forEach(function (byte) {
            result += String.fromCharCode(parseInt(byte, 16));
        });

        return result;
    }

    /**
     * Function which fills entry with easy to bind properties.
     */
    function updateEntry(entry) {
        if (entry.key && entry.value) {
            var isStruct = entry.value.typeKind == 'struct';
            entry.isCompositeObject = entry.value.container || isStruct;
            entry.template = 'tree_item_renderer.html';

            // value label
            if (entry.value.container) {
                if (entry.value.type.indexOf('mapping(') == 0) {
                    entry.valueLabel = '(size = ' + entry.value.size + ')'
                } else {
                    entry.valueLabel = /*entry.value.type + */'(size = ' + entry.value.size + ')';
                }
            } else if (isStruct) {
                entry.valueLabel = entry.value.type;
            } else {
                entry.valueLabel = entry.value.decoded ? entry.value.decoded : NULL;
                var isString = isBytesAreString(entry.value.decoded);
                if (isString) {
                    entry.type1Label = 'string';
                    entry.type2Label = entry.value.type;
                    entry.valueLabel = '"' + bytesToString(entry.value.decoded) + '"';
                    entry.isConverted = true;
                    entry.template = 'tree_string_renderer.html';
                } else {
                    entry.template = 'tree_value_renderer.html';
                }
            }

            // if need to show expand button
            if (entry.value.container && entry.value.size > 0) {
                entry.expandable = true;
            } else if (isStruct) {
                entry.expandable = true;
            } else {
                entry.expandable = false;
            }

        }
        return entry;
    }

    function showErrorToastr(topMessage, bottomMessage) {
        toastr.clear()
        toastr.options = {
            "positionClass": "toast-top-right",
            "closeButton": true,
            "progressBar": true,
            "showEasing": "swing",
            "timeOut": "4000"
        };
        toastr.error('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
    }

    function ContractsCtrl($scope, $timeout, scrollConfig, $http, jsonrpc, restService) {
        console.log('Contracts controller activated.');
        $scope.contracts = $scope.contracts || [];
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.isAddingContract = false;
        $scope.isViewingStorage = false;
        $scope.newContract = {};
        $scope.storage = {entries: [], value: {decoded: ''}};
        $scope.indexSizeString = 'n/a';

        // file upload
        $scope.files = [];
        $scope.allowFileUpload = false;

        var remove0x = Utils.Hex.remove0x;

        $scope.$on('$destroy', function() {
            console.log('Contracts controller exited.');
        });

        $timeout(function() {
            restService.Contracts.getIndexStatus().then(function(result) {
                $scope.indexSizeString = filesize(result.indexSize);
            });
        }, 100);

        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        $scope.onWatchContract = function() {
            $scope.newContract = {};
            $scope.files = [];
            $scope.isAddingContract = true;
            $scope.isViewingStorage = false;

            // reset form validation
            $scope.$broadcast('show-errors-reset');
            $scope.form.$setUntouched();
            $scope.form.$setPristine();
        };

        $scope.onBackToList = function() {
            $scope.isAddingContract = false;
            $scope.isViewingStorage = false;
            $scope.storage.entries = [];
        };

        $scope.onViewStorage = function(item) {
            $scope.isAddingContract = false;
            $scope.isViewingStorage = true;
            $scope.storage.address = item.address;
            $scope.storage.balanceString = 'n/a';
            $scope.storage.contractName = item.name;

            // #1 Load balance
            jsonrpc.request('eth_getBalance', [$scope.storage.address, 'latest'])
                .then(function(value) {
                    var ethRate = Math.pow(10, 18);
                    var cutTo = Math.pow(10, 0);
                    var convertHexToEth = function(value) { return new BigNumber(remove0x(value), 16).dividedBy(ethRate / cutTo).floor().dividedBy(cutTo).toNumber(); }

                    $scope.storage.balanceString = Utils.Format.numberWithCommas(convertHexToEth(value));
                });

            // #2 Load fields
            $http({
                method: 'GET',
                url: '/contracts/' + remove0x($scope.storage.address).toLowerCase() + '/storage',
                params: {
                    path: '',           // root of contract
                    page: 0,
                    size: PAGE_SIZE
                }
            }).then(function(result) {
                console.log('Initial contract fields');
                console.log(result.data);
                // copy values to keep binding working
                $scope.storage.entries = result.data.content
                    .map(updateEntry);
                //$scope.storage.size = result.data.size;
                //$scope.storage.number = result.data.number;
                $scope.storage.totalElements = result.data.totalElements;
            });
        };

        $scope.loadContracts = function() {
            return $http({
                method: 'GET',
                url: '/contracts/list'
            }).then(function(result) {
                console.log(result);
                $scope.contracts = (result.data || [])
                    .map(function(c) {
                        c.address = EthUtil.toChecksumAddress(c.address);
                        return c;
                    });
            });
        };

        $scope.onRemoveClick = function(item) {
            console.log('onRemoveClick');

            if (confirm("Are you sure? Stopping watching this address will remove it from listing.")) {
                $http({
                    method: 'POST',
                    url: '/contracts/' + remove0x(item.address).toLowerCase() + '/delete'
                }).then($scope.loadContracts);
            }
        };

        $scope.onAddSourceCode = function() {
            console.log('onAddSourceCode');
            $http({
                method: 'POST',
                url: '/contracts/add',
                data: {
                    address: remove0x($scope.newContract.address).toLowerCase(),
                    sourceCode: $scope.newContract.sourceCode
                }})
                .success(function(result) {
                    console.log('Add source result');
                    console.log(result);
                    if (result && result.success) {
                        return $scope.loadContracts().then($scope.onBackToList);
                    } else {
                        showErrorToastr('Upload failed', result.errorMessage || 'Unknown error');
                    }
                })
        };

        $scope.onAddFile = function() {
            $('#fileInput').click();
        };

        $('#fileInput').on('change', function(event) {

            event.preventDefault();
            var files = event.target.files;

            console.log('onAddFileEvent');
            console.log(files);

            var newArray = $scope.files.slice(0);
            Array.prototype.push.apply(newArray, files);
            $scope.files = newArray;
            $scope.allowFileUpload = $scope.files.length > 0;
            event.target.value = '';
        });

        $scope.onRemoveFile = function(file) {
            var newArray = $scope.files.slice(0);
            var index = newArray.indexOf(file);
            if (index > -1) {
                newArray.splice(index, 1);
            }
            $scope.files = newArray;
            $scope.allowFileUpload = $scope.files.length > 0;
        };

        $scope.onUploadFiles = function() {
            var formData = new FormData();
            $scope.files.forEach(function(f) {
                formData.append('contracts', f);
            });

            $http.post(
                '/contracts/' + remove0x($scope.newContract.address).toLowerCase() + '/files',
                formData,
                {
                    withCredentials: false,
                    headers: {
                        'Content-Type': undefined
                    },
                    transformRequest: angular.identity
                }
            ).success(function(result) {
                console.log('Upload complete');
                console.log(result);
                if (result && !result.success) {
                    showErrorToastr('Upload failed', result.errorMessage || 'Unknown error');
                } else {
                    return $scope.loadContracts().then($scope.onBackToList);
                }
            });
        };

        $scope.onFinalAddFiles = function() {
            // force showing validation
            $scope.form.$setSubmitted();
            $scope.$broadcast('show-errors-check-validity');

            if (!$scope.form.$valid) {
                showErrorToastr('FORM VALIDATION', 'Please fill address.');
                return;
            }

            if ($scope.files.length > 0) {
                $scope.onUploadFiles();
            } else if ($scope.newContract.sourceCode) {
                $scope.onAddSourceCode();
            } else {
                showErrorToastr('FORM VALIDATION', 'Please fill either contract source code or attach it\'s file.');
            }
        };

        $scope.loadContracts();

        function onResize() {
            console.log("Contracts page resize");

            var scrollContainer = document.getElementById("contracts-scroll-container");
            var rect = scrollContainer.getBoundingClientRect();
            var newHeight = $(window).height() - rect.top - 20;
            //$(scrollContainer).css('maxHeight', newHeight + 'px');
            $scope.scrollConfig.setHeight = newHeight;
            $timeout(function() {
                $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
            }, 10);
        }
    }

    angular.module('HarmonyApp')
        .controller('ContractsCtrl', ['$scope', '$timeout', 'scrollConfig', '$http', 'jsonrpc', 'restService', ContractsCtrl])

        /**
         * Controller for rendering contract storage in expandable tree view.
         */
        .controller('TreeController', ['$scope', '$http', '$attrs', function($scope, $http, $attrs) {
            var remove0x = Utils.Hex.remove0x;

            function load(entry, page, size) {
                return $http({
                    method: 'GET',
                    url: '/contracts/' + remove0x($scope.storage.address).toLowerCase() + '/storage',
                    //url: '/contracts/' + remove0x($scope.storage.address).toLowerCase(),
                    params: {
                        path: entry.key ? entry.key.path : "",
                        page: page,
                        size: size
                    }
                }).then(function(result) {
                    console.log('Load addition result');
                    console.log(result);
                    var loadedEntries = result.data.content;
                    var newArray = entry.entries || [];

                    if (loadedEntries.length >= entry.totalElements) {
                        // cut already existed fields if this is result of Show All request
                        // then we append only not existing ones and update view smoothly
                        loadedEntries = loadedEntries.slice(newArray.length);
                    }

                    Array.prototype.push.apply(newArray, loadedEntries.map(updateEntry));
                    // set new array object to fire binding
                    entry.entries = newArray;
                });
            }

            $scope.init = function(value) {
                value.expanded = true;
                $scope.entry = value;
                //load($scope.entry, 0, PAGE_SIZE);
            };

            $scope.onShowAll = function(entry) {
                load(entry, 0, 10000);
            };

            $scope.onLoadMore = function(entry) {
                load(entry, Math.floor(entry.entries.length / PAGE_SIZE), PAGE_SIZE);
            };

            $scope.onExpand = function(entry) {
                console.log('OnExpand');
                console.log(entry);
                entry.entries = entry.entries || [];
                entry.totalElements = entry.totalElements || 0;
                load(entry, 0, PAGE_SIZE);
                entry.expanded = !entry.expanded;
            };

            $scope.onSwitchString = function(entry) {
                entry.isConverted = !entry.isConverted;
                if (entry.isConverted) {
                    entry.type1Label = 'string';
                    entry.type2Label = entry.value.type;
                    entry.valueLabel = '"' + bytesToString(entry.value.decoded) + '"';
                } else {
                    entry.type1Label = entry.value.type;
                    entry.type2Label = 'string';
                    entry.valueLabel = entry.value.decoded;
                }
            };
        }]);
})();
