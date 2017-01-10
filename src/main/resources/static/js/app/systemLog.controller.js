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

    var LINES_LIMIT = 1000;
    var logContainer;
    var filterInput;
    var filterValue = '';
    var highlightConfig;

    function scrollToBottom() {
        var logScrollContainer = document.getElementById("log-scroll-container");
        //logScrollContainer.scrollTop = logScrollContainer.scrollHeight;
        $(logScrollContainer).mCustomScrollbar('scrollTo', 'bottom');
    }

    function highlightWord(line) {
        if (highlightConfig) {
            if (highlightConfig.words) {
                for (var wordCheck in highlightConfig.words) {
                    if (highlightConfig.words.hasOwnProperty(wordCheck)) {
                        line = line.replace(
                            wordCheck,
                            '<span style="' + highlightConfig.words[wordCheck] + '">' + wordCheck + '</span>'
                        );
                    }
                }
            }
        }
        return line;
    }

    /**
     * @return HTMLElement
     * @private
     */
    function highlightLine(line, container) {
        if (highlightConfig) {
            if (highlightConfig.lines) {
                for (var lineCheck in highlightConfig.lines) {
                    if (line.indexOf(lineCheck) !== -1) {
                        container.setAttribute('style', highlightConfig.lines[lineCheck]);
                    }
                }
            }
        }
        return container;
    }

    function SystemLogCtrl($scope, $timeout, scrollConfig) {
        // checkbox value
        $scope.isAutoScroll = true;
        $scope.scrollConfig = jQuery.extend(true, {}, scrollConfig);
        // used to batch update UI
        $scope.batchLogsTimer = null;
        $scope.pendingLogs = [];

        // checkbox change handler
        $scope.onAutoScrollChange = function() {
            console.log("Auto scroll value: " + $scope.isAutoScroll);
            if ($scope.isAutoScroll) {
                scrollToBottom();
            }
            // return focus back to filtering input
            filterInput.focus();
        };

        // handling event from main controller
        $scope.$on('systemLogEvent', function(event, data) {
            if ($scope.batchLogsTimer == null) {
                // for safety
                $scope.pendingLogs.forEach(addLogLine);
                $scope.pendingLogs = [];
                // process first log immediatelly
                addLogLine(data);
                postLogAction();
                // delay all other logs with some DELAY
                $scope.batchLogsTimer = $timeout(function() {
                    $scope.pendingLogs.forEach(addLogLine);
                    $scope.pendingLogs = [];
                    postLogAction();
                    $scope.batchLogsTimer = null;
                }, 300);
            } else {
                $scope.pendingLogs.push(data);
            }
        });
        $scope.$on('currentSystemLogs', function(event, items) {
            $scope.pendingLogs.forEach(addLogLine);
            $scope.pendingLogs = [];
            items.forEach(addLogLine);
            postLogAction();
            if ($scope.isAutoScroll) {
                scrollToBottom();
            }
        });

        init({
            container: document.getElementsByClassName('log')[0],
            filterInput: document.getElementsByClassName('query')[0]
        });

        function resizeLogContainer() {
            console.log('System Log page resize');
            var scrollContainer = document.getElementById('log-scroll-container');
            var rect = scrollContainer.getBoundingClientRect();
            var newHeight = $(window).height() - rect.top - 20;
            //$(scrollContainer).css('maxHeight', newHeight + 'px');

            $timeout(function() {
                $scope.scrollConfig.setHeight = newHeight;
                $(scrollContainer).mCustomScrollbar($scope.scrollConfig);
            }, 10);
        }

        /**
         * Resize logs element to fit all available space.
         * Otherwise many HTML changes are required to achieve same result
         */
        $(window).ready(resizeLogContainer);
        $scope.$on('windowResizeEvent', resizeLogContainer);

        /**
         * Hide element if doesn't contain filter value
         */
        function filterElement(element) {
            var pattern = new RegExp(filterValue, 'i');
            if (pattern.test(element.textContent)) {
                element.style.display = '';
            } else {
                element.style.display = 'none';
            }
        }

        /**
         * Filter logs based on filterValue
         */
        function filterLogs() {
            var collection = logContainer.childNodes;
            var i = collection.length;

            if (i === 0) {
                return;
            }

            while (i) {
                filterElement(collection[i - 1]);
                i -= 1;
            }
            if ($scope.isAutoScroll) {
                scrollToBottom();
            }
        }

        function init(opts) {
            // Elements
            logContainer = opts.container;
            filterInput = opts.filterInput;
            filterInput.focus();

            // Filter input bind
            filterInput.addEventListener('keyup', function (e) {
                // ESC
                if (e.keyCode === 27) {
                    this.value = '';
                    filterValue = '';
                } else {
                    filterValue = this.value;
                }
                filterLogs();
            });
        }

        /**
         * Main method for adding new log line
         */
        function addLogLine(data) {
            var div = document.createElement('div');
            var p = document.createElement('p');
            p.className = 'inner-line';

            // convert ansi color codes to html && escape HTML tags
            data = ansi_up.escape_for_html(data); // eslint-disable-line
            data = ansi_up.ansi_to_html(data); // eslint-disable-line
            p.innerHTML = highlightWord(data);

            div.className = 'line';
            div = highlightLine(data, div);
            div.addEventListener('click', function () {
                if (this.className.indexOf('selected') === -1) {
                    this.className = 'line-selected';
                } else {
                    this.className = 'line';
                }
            });

            div.appendChild(p);
            filterElement(div);
            logContainer.appendChild(div);
        }

        /**
         * Remove old lines and auto scroll.
         */
        function postLogAction() {
            // removing items, when auto scroll turned off, will cause logs to move
            if ($scope.isAutoScroll) {
                var len = logContainer.children.length;
                if (len > LINES_LIMIT) {
                    if (len - LINES_LIMIT > 1) {
                        for (var i = len - LINES_LIMIT; i > 0; i--) {
                            logContainer.removeChild(logContainer.children[0]);
                        }
                        console.log('Removed ' + (len - LINES_LIMIT) + ' log entries');
                    } else {
                        logContainer.removeChild(logContainer.children[0]);
                    }
                }
            }

            if ($scope.isAutoScroll) {
                scrollToBottom();
            }
        }
    }

    angular.module('HarmonyApp')
        .controller('SystemLogCtrl', ['$scope', '$timeout', 'scrollConfig', SystemLogCtrl]);
})();
