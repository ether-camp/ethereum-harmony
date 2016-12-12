/**
 * Allow use terminal component to call JSON-RPC methods.
 */

(function() {
    'use strict';

    var CONTAINER_ID = 'terminal-container';

    var terminalCompletionWords = [];
    var terminal = null;

    /**
     * @return ['method'] if ['method String String'] was passed
     */
    function extractMethods(list) {
        return list.map(function(item) {
            return item.split(' ')[0];
        });
    }

    function isCommandIn(command, array) {
        return array.some(function(item) {
            return command.indexOf(item) == 0;
        });
    }

    function TerminalCtrl($scope, $timeout, jsonrpc, scrollConfig) {
        console.log('TerminalCtrl controller activated.');

        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.suggestions = extractMethods(terminalCompletionWords);
        $scope.filteredSuggestions = extractMethods(terminalCompletionWords);
        $scope.hideSuggestionsList = false;
        $scope.hideCommandInfo = true;
        $scope.commandInfoName = null;
        $scope.commandInfoParams = null;
        $scope.txData = {};

        var defaultCurrency = 'wei';   // otherwise need to convert

        $scope.onSignAndSend = function() {
            var privateKey = $('#pkeyInput').val();
            var txData = $scope.txData;

            //console.log('onSignAndSend');
            //console.log(privateKey);
            //console.log(txData);

            RlpBuilder
                .balanceTransfer(remove0x(txData.toAddress))    // to address
                .from(remove0x(txData.fromAddress))
                .secretKey(privateKey)
                .gasLimit(txData.gasLimit)
                .gasPrice(txData.gasPrice)
                .value(txData.value, defaultCurrency)
                .nonce(txData.nonce)
                //.invokeData(data)
                .withData(txData.data)
                .format()
                .done(function (rlp) {
                    console.log('Signed transaction');
                    console.log(rlp);

                    jsonrpc.request('eth_sendRawTransaction', [rlp])
                        .then(function(result) {
                            //console.log(result);
                            console.log('eth_sendRawTransaction result:' + result);
                            terminal.echo(result);
                            $('#signWithKeyModal').modal('hide');
                        })
                        .catch(function(error) {
                            console.log('Error sending raw transaction');
                            console.log(error);
                            showErrorToastr('ERROR', 'Wasn\'t to send signed raw transaction.\n' + error);
                        });
                })
                .fail(function(error) {
                    console.log('Error signing tx ' + error);
                    showErrorToastr('ERROR', 'Wasn\'t able to sign transaction.\n' + error);
                    //$balanceDlg.find('input[name="key"]').addClass('error').focus();
                })
                .always(function () {
                    // Hide progress indicator
                    //$progressIndicator.fadeOut(function () {
                    //    $(this).remove();
                    //});
                });
        };

        var commandLinePendingUnlock = null;

        $scope.$on('$destroy', function() {
            if (terminal) {
                terminal.destroy();
            }
            console.log('TerminalCtrl controller exited.');
        });

        /**
         * Load method names for code completion if not already
         */
        if (terminalCompletionWords.length == 0) {
            jsonrpc.request('ethj_listAvailableMethods', {})
                .then(function(result) {
                    //console.log(result);
                    console.log('Result methods count available:' + result.length);
                    terminalCompletionWords = result;
                    $scope.filteredSuggestions = $scope.suggestions = extractMethods(terminalCompletionWords);
                    createTerminal(terminalCompletionWords);
                })
                .catch(function(error) {
                    console.log('Error loading available methods');
                    console.log(error);
                    createTerminal([], jsonrpc, $timeout);
                });
        } else {
            $scope.filteredSuggestions = $scope.suggestions = extractMethods(terminalCompletionWords);
            createTerminal(terminalCompletionWords);
        }


        /**
         * Resize table to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(function() {
            onResize();
            // force cleaning pkey value when modal closed
            $('#signWithKeyModal').on('hidden.bs.modal', function () {
                $('#pkeyInput').val('');
            })
        });
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
                //if (terminal) {
                //    $('#terminal-container').mCustomScrollbar($scope.scrollConfig);
                //}
            }, 10);
        }



        function createTerminal(list) {
            var methods = extractMethods(list);

            terminal = $('#' + CONTAINER_ID)
                .terminal(
                    onCommandEnter,
                    {
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
                        historyFilter: function(command) {
                            // exclude these commands as they include passwords
                            return !isCommandIn(command, ['personal_unlockAccount', 'personal_importRawKey', 'personal_newAccount']);
                        },
                        //onCommandChange: onCommandChange,
                        prompt: 'node> ',
                        onInit: function(term) {
                            term.focus();
                        }
                    });

            terminal.history = false;

            $(window).ready(onResize);
        }

        /**
         * Handle entered command.
         */
        function onCommandEnter(line, term) {
            if (line !== '') {

                var arr = line.match(/\S+/g) || [''];
                var command = arr.shift();
                var args = arr;

                // validate if command exists in list
                var originCommandRow = getOriginalCommandRow(command);
                if (!originCommandRow) {
                    term.echo('[[;#FF0000;]Command not found]');
                    return;
                }

                // workaround for `eth_compileSolidity`
                var eth_compileSolidity = 'eth_compileSolidity';
                if (command == eth_compileSolidity) {
                    var code = line.replace(eth_compileSolidity, '')
                        .trim()
                        .replace(/^"/, '')
                        .replace(/"$/, '');
                    args = [code];
                }

                // fill missing arguments with null values
                var isNotAllArguments = false;
                var originArgs = originCommandRow.split(' ');
                originArgs.shift();
                for (var i = args.length; i < originArgs.length; i++) {
                    console.log('Added optional argument to command:' + command + ', arg:' + originArgs[i]);
                    args.push(null);
                    isNotAllArguments = true;
                }
                if (isNotAllArguments) {
                    term.echo('[[;#96BC96;]WARNING: You didn\'t enter all arguments.]');
                }

                jsonrpc.request(command, args)
                    .then(function(result) {
                        console.log('JSON-RPC result');
                        console.log(result);
                        var stringResult = JSON.stringify(result, null, 2).replace(/]/g, '&#93;');

                        //term.echo("Result:");
                        term.echo('[[;#96BC96;]' + stringResult + ']');
                        $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');

                        if (isCommandIn(command, ['eth_sendTransactionArgs', 'eth_sendRawTransaction'])) {
                            // request tx receipt
                            term.echo('Requesting tx receipt via ethj_getTransactionReceipt');
                            onCommandEnter('ethj_getTransactionReceipt ' + result, term);
                        }
                    })
                    .catch(function(error) {
                        console.log(error);

                        /**
                         * Server requires password to access account.
                         * Ask user for password.
                         */
                        if (error.data && error.data.exceptionTypeName == 'com.ethercamp.harmony.util.HarmonyException') {
                            if (error.data.message && error.data.message.indexOf('Unlocked account is required') == 0) {
                                term.echo('[[;#f6a821;]' + 'Unlocked account is required. Please unlock with personal_unlockAccount'  + ']');
                            } else if (error.data.message == 'Key not found in keystore') {
                                // show modal popup
                                $scope.txData = {
                                    command:        command,
                                    fromAddress:    args[0],            // hex
                                    toAddress:      args[1],            // hex
                                    gasLimit:       hexToInt(args[2]),
                                    gasPrice:       hexToInt(args[3]),
                                    value:          hexToInt(args[4]),
                                    data:           args[5],            // hex
                                    nonce:          hexToInt(args[6])
                                };
                                console.log('Show modal');
                                console.log($scope.txData);
                                $('#signWithKeyModal').modal({});
                                commandLinePendingUnlock = line;
                            }
                        } else {
                            term.echo('[[;#FF0000;]'  +error + ']');
                        }

                        $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');
                    });
            }
        }

        /**
         * React on user input and filter suggestions.
         * If one command left, show command details panel.
         */
        function onCommandChange(line, terminal) {
            console.log('onCommandChange')
            $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');
            $timeout(function() {
                var arr = line.match(/\S+/g);
                if (arr && arr.length > 0) {
                    var command = arr.shift();
                    $scope.filteredSuggestions = $scope.suggestions
                        .filter(function(item) {
                            return item.startsWith(command);
                        });
                    if ($scope.filteredSuggestions.length == 1) {
                        $scope.commandInfoName = $scope.filteredSuggestions[0];
                        var params = getOriginalCommandRow($scope.commandInfoName).split(' ');
                        params.shift();
                        $scope.commandInfoParams = (params.length > 0 ? params.join(' ') : '');
                        $scope.hideCommandInfo = false;
                        $scope.hideSuggestionsList = true;
                    } else {
                        $scope.hideCommandInfo = true;
                        $scope.hideSuggestionsList = false;
                    }
                } else {
                    $scope.hideCommandInfo = true;
                    $scope.hideSuggestionsList = false;
                    $scope.filteredSuggestions = $scope.suggestions;
                }
            }, 10);
        }

        function getOriginalCommandRow(command) {
            return terminalCompletionWords.filter(function(w) {
                return w.startsWith(command);
            })[0];
        }

        function hexToInt(hexValue) {
            return parseInt(remove0x(hexValue), 16);
        }

        function remove0x(value) {
            if (value && value.indexOf('0x') == 0) {
                return value.substr(2);
            } else {
                return value;
            }
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
    }

    angular.module('HarmonyApp')
        .controller('TerminalCtrl', ['$scope', '$timeout', 'jsonrpc', 'scrollConfig', TerminalCtrl])
        .filter('rpcItemFilter', function() {
            return function( items, condition) {
                var filtered = [];

                if(condition === undefined || condition === ''){
                    return items;
                }
                condition = condition.toLowerCase();

                angular.forEach(items, function(item) {
                    if(item.methodName.toLowerCase().indexOf(condition) > -1){
                        filtered.push(item);
                    }
                });

                return filtered;
            };
        });
})();
