/**
 * Allows to view contract values in blockchain storage.
 */

(function() {
    'use strict';

    var PAGE_SIZE = 30;
    var NULL = '<empty>';

    function isTimestamp (value) {
        return /^1[4-9]((\d{8})|(\d{11}))$/.test('' + value);
    }

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
            var isContract = entry.value.typeKind == 'contract';
            entry.isCompositeObject = entry.value.container || isStruct;
            entry.template = 'tree_item_renderer.html';

            entry.valueType = entry.value.typeKind || entry.value.type;
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
                var decoded = entry.value.decoded;
                entry.valueLabel = decoded ? decoded : NULL;
                entry.valueSecondLabel = '';
                var isString = isBytesAreString(decoded);
                if (isContract) {
                    entry.valueType = entry.value.type + ' contract';
                } else if (isString) {
                    entry.type1Label = 'string';
                    entry.type2Label = entry.value.type;
                    entry.valueLabel = '"' + bytesToString(decoded) + '"';
                    entry.isConverted = true;
                    entry.template = 'tree_string_renderer.html';
                } else {
                    if (isTimestamp(decoded)) {
                        entry.valueSecondLabel = moment((('' + decoded).length == 10) ? (decoded * 1000) : decoded).format('(DD-MMM-YYYY HH:mm:ss Z)');
                    }
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

    function showToastr(isError, topMessage, bottomMessage) {
        toastr.clear();
        toastr.options = {
            "positionClass": "toast-top-right",
            "closeButton": true,
            "progressBar": true,
            "showEasing": "swing",
            "timeOut": "4000"
        };
        if (isError) {
            toastr.error('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
        } else {
            toastr.success('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
        }

    }

    /**
     * @example 1000 -> "1,000"
     */
    function numberWithCommas(x) {
        return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    }

    function ContractsCtrl($scope, $timeout, scrollConfig, $http, jsonrpc, restService, $q, scopeUtil, $location) {
        var UNINITIALIZED_SYNCED_BLOCK = -1;

        console.log('Contracts controller activated.');
        $scope.contracts = $scope.contracts || [];
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.isViewingStorage = false;
        $scope.storage = {entries: [], value: {decoded: ''}, blockNumber: -1};
        $scope.indexSizeString = 'n/a';
        $scope.solcVersionString = 'n/a';
        $scope.syncedBlock = UNINITIALIZED_SYNCED_BLOCK;

        // file upload
        $scope.files = [];
        $scope.allowFileUpload = false;
        $scope.submitErrorMessage = '';

        var remove0x = Utils.Hex.remove0x;

        $scope.$on('$destroy', function() {
            console.log('Contracts controller exited.');
        });

        // update status in case of synced block value changed
        $scope.$on('newBlockFromEvent', function(event, item) {
            if ($scope.syncedBlock == UNINITIALIZED_SYNCED_BLOCK) {
                loadStatus();
            }
        });

        $scope.onWatchContract = function() {
            $location.path('contractNew');
        }

        function loadStatus() {
            restService.Contracts.getIndexStatus().then(function(result) {
                $scope.indexSizeString = filesize(result.indexSize);
                $scope.solcVersionString = result.solcVersion;
                $scope.syncedBlock = result.syncedBlock;
                $scope.syncedBlockString = numberWithCommas(result.syncedBlock);
            });
        }

        $timeout(loadStatus, 100);

        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        $scope.onBackToList = function() {
            $scope.isViewingStorage = false;
            $scope.storage.entries = [];

            $scope.checkScrollsLater();
        };

        $scope.onViewStorage = function(value) {
            var contract = $scope.contracts.filter(function(item) {
                return item.address == value.address;
            })[0];

            console.log('View contact ' + contract.address + ' from #' + contract.blockNumber);

            scopeUtil.safeApply(function() {
                $scope.isViewingStorage = true;
                $scope.storage.address = contract.address;
                $scope.storage.balanceString = 'n/a';
                $scope.storage.contractName = contract.name;
                $scope.storage.blockNumber = contract.blockNumber;
                $scope.lastViewingItem = contract;
                $scope.importingError = '';
            });

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
                //console.log('Initial contract fields');
                //console.log(result.data);
                // copy values to keep binding working
                $scope.storage.entries = result.data.content
                    .map(updateEntry);
                //$scope.storage.size = result.data.size;
                //$scope.storage.number = result.data.number;
                $scope.storage.totalElements = result.data.totalElements;
                $scope.checkScrollsLater();
            });
            $scope.checkScrollsLater();
        };

        $scope.loadContracts = function() {
            var deferred = $q.defer();
            $http({
                method: 'GET',
                url: '/contracts/list'
            }).then(
                function(result) {
                    //console.log(result);
                    $scope.contracts = (result.data || [])
                        .map(function(c) {
                            c.address = EthUtil.toChecksumAddress(c.address);
                            return c;
                        });
                    deferred.resolve();
                }),
                function(error) {
                    deferred.reject(error);
                };
            return deferred.promise;
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

        $scope.onClearContract = function() {
            var lastItem = $scope.lastViewingItem;
            $http({
                    method: 'POST',
                    url: '/contracts/' + remove0x($scope.storage.address).toLowerCase() + '/clearContractStorage',
                    params: {}
            })
                .then(function() {
                    return $scope.loadContracts()
                        .then(function() {
                            $scope.onViewStorage(lastItem);
                        });
                })
        };

        $scope.onImportContract = function() {
            $scope.isImportingInProgress = true;
            $scope.importingError = '';

            var lastItem = $scope.lastViewingItem;
            $http({
                method: 'POST',
                url: '/contracts/' + remove0x($scope.storage.address).toLowerCase() + '/importFromExplorer',
                params: {}
            }).then(function(result) {
                console.log('Imported addition result');
                console.log(result);
                if (result.data.success) {
                    showToastr(false, "", "Successfully imported contract data");
                    $scope.loadContracts()
                        .then(function() {
                            $scope.onViewStorage(lastItem);
                        });
                } else {
                    $scope.importingError = result.data.errorMessage;
                    showToastr(false, "Work done", "Imported contract data");
                }
                $scope.isImportingInProgress = false;
            }).catch(function(error) {
                console.log('Import error');
                console.log(error);
                $scope.isImportingInProgress = false;
                $scope.importingError = 'Problem importing data. Server might not be available';
            });
        };

        $scope.loadContracts();

        function onResize() {
            console.log("Contracts page resize");

            ['storage-scroll-container', 'contracts-scroll-container'].forEach(function(elementId) {
                var scrollContainer = document.getElementById(elementId);
                if (!scrollContainer) {
                    return;
                }
                var rect = scrollContainer.getBoundingClientRect();
                var newHeight = $(window).height() - rect.top - 20;
                $scope.scrollConfig.setHeight = newHeight;
                $timeout(function() {
                    $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
                }, 10);
            });
        }

        // may be fixed via global html layout in future
        $scope.checkScrollsLater = function() {
            $timeout(onResize, 10);
        };
        $scope.checkScrollsLater();
    }

    angular.module('HarmonyApp')
        .controller('ContractsCtrl', ['$scope', '$timeout', 'scrollConfig', '$http', 'jsonrpc', 'restService', '$q', 'scopeUtil', '$location', ContractsCtrl])

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
                    entry.totalElements = result.data.totalElements;
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
                entry.expanded = !entry.expanded;
                if (!entry.expanded) {
                    // clear all loaded items
                    entry.entries = [];
                } else {
                    load(entry, 0, PAGE_SIZE);
                }
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
