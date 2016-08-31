/**
 * ngStomp
 *
 * @version 0.3.0
 * @author Maik Hummel <m@ikhummel.com>
 * @license MIT
 */

/*global
    angular, SockJS, Stomp */

angular
  .module('ngStomp', [])
  .service('$stomp', [
    '$rootScope', '$q',
    function ($rootScope, $q) {
      this.sock = null
      this.stomp = null
      this.debug = null

      this.setDebug = function (callback) {
        this.debug = callback
      }

      this.connect = function (endpoint, headers, errorCallback) {
        headers = headers || {}

        var dfd = $q.defer()

        this.sock = new SockJS(endpoint)
        this.stomp = Stomp.over(this.sock)
        this.stomp.debug = this.debug
        this.stomp.connect(headers, function (frame) {
          dfd.resolve(frame)
        }, function (err) {
          dfd.reject(err)
          errorCallback(err)
        })

        return dfd.promise
      }

      this.disconnect = function () {
        var dfd = $q.defer()
        this.stomp.disconnect(dfd.resolve)
        return dfd.promise
      }

      this.subscribe = this.on = function (destination, callback, headers) {
        headers = headers || {}
        return this.stomp.subscribe(destination, function (res) {
          var payload = null;
          try {
            var firstChar = res.body ? res.body[0] : null;
            if (firstChar != '{' && firstChar != '[') {
              payload = res.body;
            } else {
              payload = JSON.parse(res.body);
            }
          } finally {
            if (callback) {
              callback(payload, res.headers, res)
            }
          }
        }, headers)
      }

      //this.unsubscribe = this.off = function (subscription) {
      //  subscription.unsubscribe()
      //}

      // SR: changed unsubscribe to be like original
      this.unsubscribe = this.off = function (topic) {
        return this.stomp.unsubscribe(topic);
      };

      this.send = function (destination, body, headers) {
        var dfd = $q.defer()
        try {
          var payloadJson = JSON.stringify(body)
          headers = headers || {}
          this.stomp.send(destination, headers, payloadJson)
          dfd.resolve()
        } catch (e) {
          dfd.reject(e)
        }
        return dfd.promise
      }
    }]
)
