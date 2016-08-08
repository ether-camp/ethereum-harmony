/**
 * Render home page.
 *  - show blockchain tree chart;
 *
 */

(function() {
    'use strict';


    function HomeCtrl($scope, $timeout, scrollConfig) {

        var treeContainer = document.getElementById('blockchain-chart');

        BlockchainView
            .create(treeContainer)
            .setData([
                {
                    blockHash: '10',
                    blockNumber: 1,
                    difficulty: 10
                },
                {
                    blockHash: '11',
                    blockNumber: 1,
                    difficulty: 10
                },
                {
                    blockHash: '12',
                    blockNumber: 1,
                    difficulty: 10
                },
                {
                    blockHash: '20',
                    blockNumber: 2,
                    parentHash: '10',
                    difficulty: 10
                },
                {
                    blockHash: '30',
                    blockNumber: 3,
                    parentHash: '20',
                    difficulty: 10
                },
                {
                    blockHash: '31',
                    blockNumber: 3,
                    parentHash: '20',
                    difficulty: 10
                },
                {
                    blockHash: '32',
                    blockNumber: 3,
                    parentHash: '20',
                    difficulty: 10
                },
                {
                    blockHash: '42',
                    blockNumber: 4,
                    parentHash: '32',
                    difficulty: 10
                },
                {
                    blockHash: '44',
                    blockNumber: 4,
                    parentHash: '32',
                    difficulty: 10
                },
                {
                    blockHash: '52',
                    blockNumber: 5,
                    parentHash: '42',
                    difficulty: 10
                },
                {
                    blockHash: '62',
                    blockNumber: 6,
                    parentHash: '52',
                    difficulty: 10
                }

            ]);

    }

    angular.module('HarmonyApp')
        .controller('HomeCtrl', ['$scope', '$timeout', 'scrollConfig', HomeCtrl]);
})();
