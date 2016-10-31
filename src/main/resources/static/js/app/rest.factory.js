(function() {
    'use strict';
    angular.module('HarmonyApp').service('restService', RestService);

    RestService.$inject = ['$http', '$rootScope', '$q'];

    function RestService($http, $rootScope, $q) {

        function enhanceResult(result) {
            if (result && result.data && result.data.success) {
                return $q.resolve(result.data.result);
            } else {
                return $q.reject(result.data);
            }
        }

        return {
            Contracts: {
                getIndexStatus: function() {
                    return $http.get('/contracts/indexStatus').then(enhanceResult);
                }
            }
        };
    }
})();
