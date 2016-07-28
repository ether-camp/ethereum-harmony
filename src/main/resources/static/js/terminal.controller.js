/**
 * Allows use terminal component to call JSON-RPC methods.
 */

(function() {
    'use strict';

    var CONTAINER_ID = 'terminal-container';

    var terminalCompletionWords = null;
    var terminal = null;

    /**
     * @return ['method'] if ['method String String'] was passed
     */
    function extractMethods(list) {
        return list.map(function(item) {
            return item.split(" ")[0];
        });
    }

    function TerminalCtrl($scope, $timeout, jsonrpc, scrollConfig) {
        console.log('TerminalCtrl controller activated.');

        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.suggestions = terminalCompletionWords;
        $scope.filteredSuggestions = terminalCompletionWords;

        $scope.$on('$destroy', function() {
            console.log('TerminalCtrl controller exited.');
        });

        // load method names for code completion if not already
        if (terminalCompletionWords == null) {
            jsonrpc.request('listAvailableMethods', {})
                .then(function(result) {
                    //console.log(result);
                    console.log('Result methods count available:' + result.length);
                    terminalCompletionWords = result;
                    $scope.filteredSuggestions = $scope.suggestions = terminalCompletionWords;
                    createTerminal(terminalCompletionWords);
                })
                .catch(function(error) {
                    console.log('Error loading available methods');
                    console.log(error);
                    createTerminal([], jsonrpc, $timeout);
                });
        } else {
            $scope.filteredSuggestions = $scope.suggestions = terminalCompletionWords;
            createTerminal(terminalCompletionWords);
        }


        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(onResize);
        $scope.$on('windowResizeEvent', onResize);

        function onResize() {
            console.log("TerminalCtrl page resize");

            var newHeight = $(window).height();
            var scrollContainer = document.getElementById('terminal-parent');
            var rect = scrollContainer.getBoundingClientRect();
            var suggestionScrollContainer = document.getElementById('suggestion-scroll-container');
            var newHeight = (newHeight - rect.top - 30);

            //$(suggestionScrollContainer).css('maxHeight', height + 'px');

            if (terminal) {
                terminal.resize(rect.width - 20, newHeight);
            }
            $timeout(function() {
                $scope.scrollConfig.setHeight = newHeight;
                $('#suggestion-scroll-container').mCustomScrollbar($scope.scrollConfig);
                if (terminal) {
                    $('#terminal-container').mCustomScrollbar($scope.scrollConfig);
                }
            }, 10);

        }



        function createTerminal(list) {
            var methods = extractMethods(list);

            terminal = $('#' + CONTAINER_ID).terminal(function(line, term) {
                if (line !== '') {
                    var arr = line.match(/\S+/g);
                    if (arr && arr.length > 0) {
                        var command = arr.shift();
                        var args = arr;

                        jsonrpc.request(command, args)
                            .then(function(result) {
                                console.log('JSON-RPC result');
                                console.log(result);
                                //term.echo("Result:");
                                term.echo(JSON.stringify(result));
                                $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');
                            })
                            .catch(function(error) {
                                console.log(error);
                                term.echo('[[;#FF0000;]'  +error + ']');
                                $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');
                            });
                    }
                }
            }, {
                greetings: 'Ethereum',
                name: 'ethereum_terminal',
                tabcompletion: true,
                history: false,
                completion: function(terminal, command, callback) {
                    callback(methods);
                },
                keydown: function(event, terminal) {
                    $timeout(function() {
                        onCommandChange(terminal.get_command(), terminal);
                    }, 10);
                },
                //onCommandChange: onCommandChange,
                prompt: 'node> '
            });

            terminal.history = false;

            $(window).ready(onResize);
        }

        function onCommandChange(line, terminal) {
            $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');
            $timeout(function() {
                var arr = line.match(/\S+/g);
                if (arr && arr.length > 0) {
                    var command = arr.shift();
                    $scope.filteredSuggestions = $scope.suggestions
                        .filter(function(item) {
                            return item.split(" ")[0].startsWith(command);
                        });
                } else {
                    $scope.filteredSuggestions = $scope.suggestions;
                }
            }, 10);
        }
    }

    angular.module('HarmonyApp')
        .controller('TerminalCtrl', ['$scope', '$timeout', 'jsonrpc', 'scrollConfig', TerminalCtrl]);
})();
