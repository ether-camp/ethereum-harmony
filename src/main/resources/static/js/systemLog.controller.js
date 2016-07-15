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
        logScrollContainer.scrollTop = logScrollContainer.scrollHeight;
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

    function resizeLogContainer() {
        console.log("System Log page resize");
        var logScrollContainer = document.getElementById("log-scroll-container");
        var rect = logScrollContainer.getBoundingClientRect();
        var newHeight = $(window).height();
        $(logScrollContainer).css('maxHeight', (newHeight - rect.top - 30) + 'px');
    }

    function SystemLogCtrl($scope, $timeout) {
        // checkbox value
        $scope.isAutoScroll = true;

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
            //console.log("systemLogEvent " + data);
            log(data);
        });

        init({
            container: document.getElementsByClassName('log')[0],
            filterInput: document.getElementsByClassName('query')[0]
        });


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
        function log(data) {
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

            if (logContainer.children.length > LINES_LIMIT) {
                logContainer.removeChild(logContainer.children[0]);
            }

            if ($scope.isAutoScroll) {
                scrollToBottom();
            }
        }
    }

    angular.module('HarmonyApp')
        .controller('SystemLogCtrl', ['$scope', '$timeout', SystemLogCtrl]);
})();
