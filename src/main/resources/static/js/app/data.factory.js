var $ = require('jquery'),
    angular = require('angular');


angular.module('HarmonyApp').service('DataService', DataService);

DataService.$inject = ['$http', '$rootScope'];

function DataService ($http, $rootScope) {


    var service = {
    };
    return service;
}

exports.DataService = DataService;
