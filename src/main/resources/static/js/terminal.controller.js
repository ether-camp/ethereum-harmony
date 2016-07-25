/**
 * Rendering list of API usage stats.
 */

(function() {
    'use strict';

    var CONTAINER_ID = 'terminal-container';

    function onResize() {
        console.log("TerminalCtrl page resize");

        var scrollContainer = document.getElementById(CONTAINER_ID);
        var rect = scrollContainer.getBoundingClientRect();
        var newHeight = $(window).height();
        $(scrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
    }

    function TerminalCtrl($scope, $timeout) {
        console.log('TerminalCtrl controller activated.');
        $scope.rpcItems = $scope.rpcItems;

        $scope.$on('$destroy', function() {
            console.log('RpcUsage controller exited.');
        });

        var container = $('<div class="console">');
        $('#' + CONTAINER_ID).append(container);
        var consoleContainer = document.getElementById(CONTAINER_ID);
        var controller = container.console({
            promptLabel: 'Demo> ',
            commandValidate:function(line){
                if (line == "") return false;
                else return true;
            },
            commandHandle:function(line){
                return [{msg:"=> [12,42]",
                    className:"jquery-console-message-value"},
                    {msg:":: [a]",
                        className:"jquery-console-message-type"}]
            },
            autofocus:true,
            animateScroll:true,
            promptHistory:true,
            charInsertTrigger:function(keycode,line){
                // Let you type until you press a-z
                // Never allow zero.
                return !line.match(/[a-z]+/) && keycode != '0'.charCodeAt(0);
            }
        });

        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        /**
         * Received stats update from server
         */
        $scope.$on('123', function(event, items) {
            angular.forEach(items, function(item) {
                item.lastTime = item.lastTime > 0 ? moment(item.lastTime).fromNow() : '';
            });

            $timeout(function() {
                $scope.rpcItems = items;
            }, 10);
        });
    }

    angular.module('HarmonyApp')
        .controller('TerminalCtrl', ['$scope', '$timeout', TerminalCtrl]);
})();
