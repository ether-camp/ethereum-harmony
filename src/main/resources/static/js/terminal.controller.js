/**
 * Allows use terminal component to call JSON-RPC methods.
 */

(function() {
    'use strict';

    var CONTAINER_ID = 'terminal-container';

    var terminalCompletitionWords = null;
    var terminal = null;

    function onResize() {
        console.log("TerminalCtrl page resize");

        var scrollContainer = document.getElementById('terminal-parent');
        var rect = scrollContainer.getBoundingClientRect();
        var newHeight = $(window).height();
        //$(scrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
        if (terminal) {
            terminal.resize(rect.width - 30, (newHeight - rect.top - 30));
        }

    }

    function createTerminal(methods, jsonrpc) {
        terminal = $('#' + CONTAINER_ID).terminal(function(command, term) {
            if (command !== '') {
                jsonrpc.request(command, {})
                    .then(function(result) {
                        console.log('JSON-RPC result');
                        console.log(result);
                        //term.echo("Result:");
                        term.echo(JSON.stringify(result));
                    })
                    .catch(function(error) {
                        term.echo('[[;#FF0000;]'  +error + ']');
                    });
            }
        }, {
            greetings: 'Ethereum',
            name: 'ethereum_terminal',
            tabcompletion: true,
            completion: function(terminal, command, callback) {
                callback(methods);
            },
            prompt: 'node> '
        });
        $(window).ready(onResize);
    }

    function TerminalCtrl($scope, $timeout, jsonrpc) {
        console.log('TerminalCtrl controller activated.');

        $scope.$on('$destroy', function() {
            console.log('TerminalCtrl controller exited.');
        });

        // load words for code completion if not already
        if (terminalCompletitionWords == null) {
            jsonrpc.request('listAvailableMethods', {})
                .then(function(result) {
                    //console.log(result);
                    console.log('Result methods count available:' + result.length);
                    terminalCompletitionWords = result;
                    createTerminal(terminalCompletitionWords, jsonrpc);
                })
                .catch(function(error) {
                    console.log('Error loading available methods');
                    console.log(error);
                    createTerminal([], jsonrpc);
                });
        } else {
            createTerminal(terminalCompletitionWords, jsonrpc);
        }


        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);
    }

    angular.module('HarmonyApp')
        .controller('TerminalCtrl', ['$scope', '$timeout', 'jsonrpc', TerminalCtrl]);
})();
