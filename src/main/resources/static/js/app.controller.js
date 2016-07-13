(function() {
    'use strict';
    angular.module('HarmonyApp').controller('AppCtrl', AppCtrl);

    AppCtrl.$inject = ['$scope', '$timeout', 'DataService'];

    function AppCtrl ($scope, $timeout, DataService) {
        var vm = this;

        var connectionLostOnce = false;

        vm.data = {
            cpuUsage: 0,
            memoryOccupied: "",
            memoryFree: "",
            freeSpace: ""
        };

        var stompClient = null;

        function setConnected(connected) {
            console.log("Connected status " + connected);
        }

        function connect() {
            console.log("Attempting to connect");

            var socket = new SockJS('/websocket');
            stompClient = Stomp.over(socket);
            stompClient.connect(
                {
                    //server: "ws://127.0.0.1:8080/ws"
                },
                function(frame) {
                    setConnected(true);
                    if (connectionLostOnce) {
                        showToastr("Connection established", "");
                    }

                    console.log('Connected: ' + frame);

                    // get immediate result
                    stompClient.subscribe('/app/machineInfo', onMachineInfoResult);
                    // subscribe for updates
                    stompClient.subscribe('/topic/machineInfo', onMachineInfoResult);
                    stompClient.subscribe('/topic/blockchainInfo', onBlockchainInfoResult);
                },
                function(error) {
                    disconnect();
                }
            );
        }

        function onMachineInfoResult(data) {
            var info = JSON.parse(data.body);

            $timeout(function() {
                vm.data.cpuUsage = info.cpuUsage;
                vm.data.memoryOccupied = filesize(info.memoryTotal - info.memoryFree);
                vm.data.memoryFree = filesize(info.memoryFree);
                vm.data.freeSpace = filesize(info.freeSpace);

                var memoryPercentage = 0;
                if (info.memoryTotal != 0) {
                    memoryPercentage = Math.round(100 * (info.memoryTotal - info.memoryFree) / info.memoryTotal);
                }
                console.log(filesize(info.memoryTotal), filesize(info.memoryFree));

                updateProgressBar('#memoryUsageProgress', memoryPercentage);
                updateProgressBar('#cpuUsageProgress', info.cpuUsage);

                console.log("memoryPercentage " + memoryPercentage);
                console.log("cpuUsage " + info.cpuUsage);

            }, 10);
        }

        var simpleSuffixes = {
            suffixes: {
                B: "",
                KB: "K",
                MB: "M",
                GB: "G",
                TB: "T"
            }
        };

        function onBlockchainInfoResult(data) {
            var info = JSON.parse(data.body);

            $timeout(function() {
                updateBlockCounter(info.lastBlockNumber);
                vm.data.lastBlockNumber         = info.lastBlockNumber;
                vm.data.lastBlockTime           = info.lastBlockTime;
                vm.data.lastBlockTimeMoment     = moment(info.lastBlockTime * 1000).fromNow();
                vm.data.lastBlockTimeString     = moment(info.lastBlockTime * 1000).format('ss:mm:hh MMM. d, YYYY');
                vm.data.lastBlockTransactions   = info.lastBlockTransactions;
                vm.data.difficulty              = filesize(info.difficulty, simpleSuffixes);
                vm.data.lastReforkTime          = info.lastReforkTime;
                vm.data.networkHashRate         = filesize(info.networkHashRate, simpleSuffixes) + "H/s";
            }, 10);
        }

        function updateBlockCounter(value) {
            var blockCounter = $('#blockCounter');
            blockCounter.prop('Counter', blockCounter.attr('value')).stop().animate({
                Counter: '' + value
            }, {
                duration: 1500,
                easing: 'linear',
                step: function(now) {
                    var value = Math.ceil(now);
                    blockCounter.attr('value', value);
                    blockCounter.text(numberWithCommas(value));
                }
            });
        }

        function numberWithCommas(x) {
            return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        }

        function updateProgressBar(view, percentage) {
            $(view).css('width', percentage + "%");
            $(view).attr('aria-valuenow', percentage);
        }

        function disconnect() {
            connectionLostOnce = true;
            showToastr("Connection Lost", "Reconnecting...");

            if (stompClient != null) {
                stompClient.disconnect();
            }
            setConnected(false);
            console.log("Disconnected. Retry ...");
            setTimeout(connect, 2000);
        }

        function showToastr(topMessage, bottomMessage) {
            toastr.clear()
            toastr.options = {
                "positionClass": "toast-top-right",
                "closeButton": true,
                "progressBar": true,
                "showEasing": "swing",
                "timeOut": "6000"
            };
            toastr.warning('<strong>' + topMessage + '</strong> <br/><small>' + bottomMessage + '</small>');
        }

        connect();
    }
})();