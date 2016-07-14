/**
 * Rendering logs component with features:
 *  - auto-scroll to bottom;
 *  - filtering by string;
 *  - line selection.
 *
 * Log UI and logic were used from https://github.com/mthenw/frontail
 */

(function() {
    'use strict';
    angular.module('HarmonyApp')
        .controller('ServerLogCtrl', ['$scope', '$timeout', function($scope, $timeout) {

            var _logContainer;
            var _filterInput;
            var _filterValue = '';
            var _linesLimit = 1000;
            var _highlightConfig;
            var logScrollContainer = document.getElementById("log-scroll-container");


            // Checkbox
            $scope.isAutoScroll = true;

            $scope.onAutoScrollChange = function() {
                console.log("Auto scroll value: " + $scope.isAutoScroll);
                if ($scope.isAutoScroll) {
                    scrollToBottom();
                }
                // return focus back to filtering input
                _filterInput.focus();
            };

            // handling event from main controller
            $scope.$on('serverLogEvent', function(event, data) {
                //console.log("serverLogEvent " + data);
                log(data);
            });


            /**
             * Resize logs element to fit all available space.
             * Otherwise many HTML changes are required to achieve same result
             */
            $(window).ready(resizeLogContainer);
            $(window).resize(resizeLogContainer);
            function resizeLogContainer() {
                var rect = logScrollContainer.getBoundingClientRect();
                var newHeight = $(window).height();
                $(logScrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
            }

            /**
             * Hide element if doesn't contain filter value
             */
            var _filterElement = function (element) {
                var pattern = new RegExp(_filterValue, 'i');
                if (pattern.test(element.textContent)) {
                    element.style.display = '';
                } else {
                    element.style.display = 'none';
                }
            };

            /**
             * Filter logs based on _filterValue
             */
            var _filterLogs = function () {
                var collection = _logContainer.childNodes;
                var i = collection.length;

                if (i === 0) {
                    return;
                }

                while (i) {
                    _filterElement(collection[i - 1]);
                    i -= 1;
                }
                if ($scope.isAutoScroll) {
                    scrollToBottom();
                }
            };

            function scrollToBottom() {
                logScrollContainer.scrollTop = logScrollContainer.scrollHeight;
            }

            var _highlightWord = function (line) {
                if (_highlightConfig) {
                    if (_highlightConfig.words) {
                        for (var wordCheck in _highlightConfig.words) {
                            if (_highlightConfig.words.hasOwnProperty(wordCheck)) {
                                line = line.replace(
                                    wordCheck,
                                    '<span style="' + _highlightConfig.words[wordCheck] + '">' + wordCheck + '</span>'
                                );
                            }
                        }
                    }
                }

                return line;
            };

            /**
             * @return HTMLElement
             * @private
             */
            var _highlightLine = function (line, container) {
                if (_highlightConfig) {
                    if (_highlightConfig.lines) {
                        for (var lineCheck in _highlightConfig.lines) {
                            if (line.indexOf(lineCheck) !== -1) {
                                container.setAttribute('style', _highlightConfig.lines[lineCheck]);
                            }
                        }
                    }
                }

                return container;
            };

            init({
                container: document.getElementsByClassName('log')[0],
                filterInput: document.getElementsByClassName('query')[0],
                body: document.getElementsByTagName('body')[0]
            });

            function init(opts) {

                // Elements
                _logContainer = opts.container;
                _filterInput = opts.filterInput;
                _filterInput.focus();

                // Filter input bind
                _filterInput.addEventListener('keyup', function (e) {
                    // ESC
                    if (e.keyCode === 27) {
                        this.value = '';
                        _filterValue = '';
                    } else {
                        _filterValue = this.value;
                    }
                    _filterLogs();
                });
            }

            /**
             * Main method for adding new log line
             */
            function log(data) {
                var div = document.createElement('div');
                var p = document.createElement('p');
                p.className = 'inner-line';

                // convert ansi color codes to html && escape HTML tags
                data = ansi_up.escape_for_html(data); // eslint-disable-line
                data = ansi_up.ansi_to_html(data); // eslint-disable-line
                p.innerHTML = _highlightWord(data);

                div.className = 'line';
                div = _highlightLine(data, div);
                div.addEventListener('click', function () {
                    if (this.className.indexOf('selected') === -1) {
                        this.className = 'line-selected';
                    } else {
                        this.className = 'line';
                    }
                });

                div.appendChild(p);
                _filterElement(div);
                _logContainer.appendChild(div);

                if (_logContainer.children.length > _linesLimit) {
                    _logContainer.removeChild(_logContainer.children[0]);
                }

                if ($scope.isAutoScroll) {
                    scrollToBottom();
                }
            }
        }]);
})();
