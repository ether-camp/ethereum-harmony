(function() {
    'use strict';
    angular.module('HarmonyApp').controller('AppCtrl', AppCtrl);

    AppCtrl.$inject = ['$scope', '$timeout', 'DataService'];

    function AppCtrl ($scope, $timeout, DataService) {
        var vm = this;

        var connectionLostOnce = false;

        vm.data = {
            cpuUsage: 0,
            memoryUsage: 0,
            memoryTotal: 0,
            diskUsage: 0
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
                vm.data.memoryUsage = filesize(info.memoryUsage);
                vm.data.memoryTotal = filesize(info.memoryTotal);
                vm.data.diskUsage = filesize(info.diskUsage);

                var memoryPercentage = info.memoryTotal == 0 ? 0 : Math.round(100 * info.memoryUsage / info.memoryTotal);

                updateProgressBar('#memoryUsageProgress', memoryPercentage);
                updateProgressBar('#cpuUsageProgress', info.cpuUsage);

                console.log("memoryPercentage " + memoryPercentage);
                console.log("cpuUsage " + info.cpuUsage);

            }, 10);
        }

        function updateProgressBar(view, percentage, label) {
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