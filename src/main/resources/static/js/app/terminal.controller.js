/**
 * Allows use terminal component to call JSON-RPC methods.
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

    function TerminalCtrl($scope, $timeout, jsonrpc, scrollConfig) {
        console.log('TerminalCtrl controller activated.');

        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        $scope.suggestions = extractMethods(terminalCompletionWords);
        $scope.filteredSuggestions = extractMethods(terminalCompletionWords);
        $scope.hideSuggestionsList = false;
        $scope.hideCommandInfo = true;
        $scope.commandInfoName = null;
        $scope.commandInfoParams = null;

        var privateKey = "0xc85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
        //var privateKey = "cow";
        var currency = 'wei';   // otherwise need to convert

        //require("./js/eth/eth-utils.js");
        $scope.onSignTransaction = function(fromAddress, toAddress, gasLimit,
                                            gasPrice, value, data, nonce) {
            RlpBuilder.balanceTransfer(toAddress)
                .from(fromAddress)
                .secretKey(privateKey)
                .gasLimit(gasLimit)
                .gasPrice(gasPrice)
                .value(value, currency)
                .nonce(nonce)
                //.invokeData(data)
                .format()
                .done(function (rlp) {
                    console.log('Signed transaction');
                    console.log(rlp);

                    jsonrpc.request('eth_sendRawTransaction', [rlp])
                        .then(function(result) {
                            //console.log(result);
                            console.log('eth_sendRawTransaction result:' + result);
                        })
                        .catch(function(error) {
                            console.log('Error sending raw transaction');
                            console.log(error);
                        });

                    return;
                    var rlpEnc = new Buffer.Buffer(rlp, 'hex');
                    var hash = new SHA3.SHA3Hash();
                    hash.update(rlpEnc);
                    var rlpHash = hash.digest().toString('hex');

                    console.log("Finished signing rlp" + rlp);
                    console.log("Finished signing rlpHash" + rlpHash);

                    // Init RLP dialog data
                    $rlpDlg.find('.rlp-hash').text(rlpHash);
                    $rlpDlg.find('.raw-data').text(rlp);

                    $rlpDlg.find('.back-button').click(function () {
                        $('.line-numbers').empty();
                        $('.formatted-data').empty();
                        $('.rlp-hash').empty();

                        dialogUtils.slideBack($rlpDlg, $balanceDlg);
                    });

                    $rlpDlg.find('.formatted-data')
                        .empty()
                        .append(txDialog.renderRLP(rlp));

                    dialogUtils.slideForward($balanceDlg, $rlpDlg);
                })
                .fail(function(error) {
                    console.log('Error signing tx ' + error);
                    //Message.showError(error);
                    //$balanceDlg.find('input[name="key"]').addClass('error').focus();
                })
                .always(function () {
                    // Hide progress indicator
                    //$progressIndicator.fadeOut(function () {
                    //    $(this).remove();
                    //});
                });
        };

        //$scope.onSignTransaction(
        //    'cd2a3d9f938e13cd947ec05abc7fe734df8dd826',
        //    '79b08ad8787060333663d19704909ee7b1903e58',
        //    '0x300000',
        //    '0x10000000000',
        //    '0x7777',
        //    '0x1',
        //    '0x101F15');

        $scope.onSignTransaction(
            'cd2a3d9f938e13cd947ec05abc7fe734df8dd826',
            '79b08ad8787060333663d19704909ee7b1903e58',
            3145728,
            1099511627776,
            40001,
            '0x1',
            1056546);

        var commandLinePendingUnlock = null;

        $scope.$on('$destroy', function() {
            console.log('TerminalCtrl controller exited.');
        });

        /**
         * Load method names for code completion if not already
         */
        if (terminalCompletionWords.length == 0) {
            jsonrpc.request('listAvailableMethods', {})
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
                        //onCommandChange: onCommandChange,
                        prompt: 'node> '
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

                // fill missing arguments with null values
                var originArgs = originCommandRow.split(' ');
                originArgs.shift();
                for (var i = args.length; i < originArgs.length; i++) {
                    console.log('Added optional argument to command:' + command + ', arg:' + originArgs[i]);
                    args.push(null);
                }

                jsonrpc.request(command, args)
                    .then(function(result) {
                        console.log('JSON-RPC result');
                        console.log(result);
                        var stringResult = JSON.stringify(result);

                        //term.echo("Result:");
                        term.echo(stringResult);
                        $('#terminal-container').mCustomScrollbar('scrollTo', 'bottom');
                    })
                    .catch(function(error) {
                        console.log(error);

                        /**
                         * Server requires password to access account.
                         * Ask user for password.
                         */
                        if (error.data && error.data.exceptionTypeName == 'com.ethercamp.harmony.util.HarmonyException') {
                            if (error.data.message == 'Unlocked account is required') {
                                term.echo('[[;#f6a821;]' + 'Unlocked account is required. Please unlock with personal_unlockAccount'  + ']');
                            } else if (error.data.message == 'Key not found in keystore') {
                                // show modal popup
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
    }

    angular.module('HarmonyApp')
        .controller('TerminalCtrl', ['$scope', '$timeout', 'jsonrpc', 'scrollConfig', TerminalCtrl]);
})();
