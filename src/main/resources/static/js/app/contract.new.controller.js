/**
 * Allows to view contract values in blockchain storage.
 */

(function() {
    'use strict';

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

    function ContractNewCtrl($scope, $timeout, scrollConfig, $http, $location, restService, $q, scopeUtil, $rootScope) {
        var UNINITIALIZED_SYNCED_BLOCK = -1;

        var vm = this;

        console.log('ContractNewCtrl controller activated.');
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.newContract = {};
        $scope.indexSizeString = $scope.indexSizeString || 'n/a';
        $scope.solcVersionString = $scope.solcVersionString || 'n/a';
        $scope.syncedBlock = UNINITIALIZED_SYNCED_BLOCK;

        // file upload
        $scope.files = [];
        $scope.allowFileUpload = false;
        $scope.submitErrorMessage = '';

        var remove0x = Utils.Hex.remove0x;

        $scope.$on('$destroy', function() {
            console.log('ContractNewCtrl controller exited.');
        });

        // update status in case of synced block value changed
        $scope.$on('newBlockFromEvent', function(event, item) {
            if ($scope.syncedBlock == UNINITIALIZED_SYNCED_BLOCK) {
                loadStatus();
            }
        });

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

        $scope.onWatchContract = function() {
            $scope.newContract = {};

            // reset form validation
            $scope.$broadcast('show-errors-reset');
            $scope.newContractForm.$setUntouched();
            $scope.newContractForm.$setPristine();
            resetFormError();
        };

        $scope.$on('$viewContentLoaded', function() {
            // form is ready, let's setup validation
            $scope.onWatchContract();
        });

        $scope.onBackToList = function() {
            $location.path('/contracts');
        };

        $scope.onAddSourceCode = function() {
            resetFormError();
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
                        $location.path('/contracts');
                    } else {
                        showFormError('Upload failed', result.errorMessage || 'Unknown error');
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
            resetFormError();

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
                    showFormError('Upload failed', result.errorMessage || 'Unknown error');
                } else {
                    //return $scope.loadContracts().then($scope.onBackToList);
                    $location.path('/contracts');
                }
            });
        };

        $scope.onFinalAddFiles = function() {
            resetFormError();
            // force showing validation

            $scope.newContractForm.$setSubmitted();
            $scope.$broadcast('show-errors-check-validity');

            if (!$scope.newContractForm.$valid) {
                showFormError('FORM VALIDATION', 'Please fill address.');
                return;
            }

            if ($scope.files.length > 0) {
                $scope.onUploadFiles();
            } else if ($scope.newContract.sourceCode) {
                $scope.onAddSourceCode();
            } else {
                showFormError('FORM VALIDATION', 'Please either fill contract source code or attach it\'s file.');
            }
        };

        function onResize() {
            console.log("Contracts page resize");

            ['newcontract-scroll-container'].forEach(function(elementId) {
                var scrollContainer = document.getElementById(elementId);
                if (!scrollContainer) {
                    return;
                }
                var rect = scrollContainer.getBoundingClientRect();
                var newHeight = $(window).height() - rect.top - 20;
                //$(scrollContainer).css('maxHeight', newHeight + 'px');
                $scope.scrollConfig.setHeight = newHeight;
                $timeout(function() {
                    $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
                }, 10);
            });
        }

        function resetFormError() {
            $scope.submitErrorMessage = '';
        }

        function showFormError(topMessage, bottomMessage) {
            $scope.submitErrorMessage = bottomMessage;
        }

        // can be fixed via global html layout changed
        $scope.checkScrollsLater = function() {
            $timeout(onResize, 10);
        };
        $scope.checkScrollsLater();
    }

    angular.module('HarmonyApp')
        .controller('ContractNewCtrl', ['$scope', '$timeout', 'scrollConfig', '$http', '$location', 'restService', '$q', 'scopeUtil', '$rootScope', ContractNewCtrl])
})();
