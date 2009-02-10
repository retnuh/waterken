/*
 * Copyright 2007-2009 Tyler Close under the terms of the MIT X license found
 * at http://www.opensource.org/licenses/mit-license.html
 *
 * web_send.js version: 2009-02-09
 *
 * This library doesn't actually pass the ADsafe verifier, but rather is
 * designed to provide a safe interface to the network, that can be loaded as
 * an ADsafe library.
 */
"use strict";
ADSAFE.lib('web', function () {

    /**
     * Construct a rejected promise.
     * @param reason    reason promise will not be fulfilled
     */
    function reject(reason) {
        var self = function (op, arg1, arg2, arg3) {
            if (undefined === op) {
                return {
                    $: [ 'org.ref_send.promise.Rejected' ],
                    reason: reason
                };
            }
            if ('WHEN' === op) { return arg2 ? arg2(reason) : self; }
            return arg1(self);
        };
        return self;
    }

    /**
     * secret slot to extract the URL from a promise
     * <p>
     * Invoking a promise puts the URL in the slot.
     * </p>
     */
    var unsealedURLref = null;

    function proxy(URLref) {
        var self = function (op, arg1, arg2, arg3) {
            if (undefined === op) {
                unsealedURLref = URLref;
                return self;
            }
            if ('WHEN' === op) {
                if (/#o=/.test(URLref)) {
                    send(URLref, 'GET', function (value) {
                        if (typeof value === 'function') {
                            value(op, arg1, arg2, arg3);
                        } else {
                            arg1(value);
                        }
                    }, '.');
                } else {
                    arg1(self);
                }
            } else {
                send(URLref, op, arg1, arg2, arg3);
            }
        };
        return self;
    }

    /**
     * Produce the JSON text for a JSON object.
     * @param argv  JSON object to serialize
     */
    function serialize(argv) {
        return JSON.stringify(argv, function (key, value) {
            switch (typeof value) {
            case 'function':
                unsealedURLref = null;
                value = value();
                if (null !== unsealedURLref) {
                    value = { '@' : unsealedURLref };
                }
                unsealedURLref = null;
                break;
            case 'number':
                if (!isFinite(value)) {
                    value = {
                        $: [ 'org.ref_send.promise.Rejected' ],
                        reason: { $: [ 'NaN' ] }
                    };
                }
                break;
            }
            return value;
        }, ' ');
    }

    /**
     * Resolves a relative URL reference.
     * @param base  absolute base URL
     * @param href  relative URL to resolve
     */
    function resolveURI(base, href) {
        base = /^[^#]*/.exec(base)[0];  // never include base fragment

        if ('' === href) { return base; }
        if (/^#/.test(href)) { return base + href; }
        if (/^[a-zA-Z][\w\-\.\+]*:/.test(href)) { return href; }
        if (/^\/\//.test(href)) {
            return /^[a-zA-Z][\w\-\.\+]*:/.exec(base)[0] + href;
        }
        if (/^\//.test(href)) {
            return /^[a-zA-Z][\w\-\.\+]*:\/\/[^\/]*/.exec(base)[0] + href;
        }

        base = /^[^\?]*/.exec(base)[0]; // drop base query
        if (/^\?/.test(href)) { return base + href; }

        // unwind relative path operators
        base = base.substring(0, base.lastIndexOf('/') + 1);
        var parts = /^([a-zA-Z][\w\-\.\+]*:\/\/[^\/]*\/)(.*)$/.exec(base);
        var host = parts[1];
        var path = parts[2];
        while (true) {
            if ('../' === href.substring(0, '../'.length)) {
                path = path.substring(0, path.lastIndexOf('/',path.length-2)+1);
                href = href.substring('../'.length);
            } else if ('./' === href.substring(0, './'.length)) {
                href = href.substring('./'.length);
            } else {
                break;
            }
        }
        if (/^\.\.(#|\?|$)/.test(href)) {
            path = path.substring(0, path.lastIndexOf('/', path.length-2) + 1);
            href = href.substring('..'.length);
        }
        if (/^\.(#|\?|$)/.test(href)) {
            href = href.substring('.'.length);
        }
        return host + path + href;
    }

    /**
     * Deserializes the return value from an HTTP response.
     * @param base  base URL for request
     * @param http  HTTP response
     */
    function deserialize(base, http) {
        switch (http.status) {
        case 200:
        case 201:
        case 202:
        case 203:
            var contentType = http.getResponseHeader('Content-Type');
            if (/^application\/do-not-execute$/i.test(contentType)) {
                return http.responseText;
            }
            return JSON.parse(http.responseText, function (key, value) {
                if (null === value) { return value; }
                if ('object' !== typeof value) { return value; }
                if (value.hasOwnProperty('@')) {
                    return proxy(resolveURI(base, value['@']));
                }
                if (value.hasOwnProperty('$')) {
                    var $ = value.$;
                    for (var i = 0; i !== $.length; ++i) {
                        if ($[i] === 'org.ref_send.promise.Rejected') {
                            return reject(value.reason);
                        }
                    }
                }
                return value;
            })[0];
        case 204:
        case 205:
            return null;
        case 303:
            var see = http.getResponseHeader('Location');
            return see ? proxy(resolveURI(base, see)) : null;
        default:
            return reject({
                $: [ 'org.ref_send.promise.Failure', 'NaO' ],
                status: http.status,
                phrase: http.statusText
            });
        }
    }

    /**
     * Enqueues an HTTP request.
     * @param URLref    target URLref
     * @param op        HTTP verb
     * @param resolve   response resolver
     * @param q         query parameter value
     * @param argv      JSON value for request body
     */
    var send = (function () {
        var active = false;
        var pending = [];
        var http;
        if (window.XMLHttpRequest) {
            http = new XMLHttpRequest();
        } else {
            http = new ActiveXObject('MSXML2.XMLHTTP.3.0');
        }
        var sessions = { /* origin => session */ };

        var output = function () {
            var m = pending[0];
            var fq = /#(.*)$/.exec(m.URLref);
            var query = fq ? fq[1] : '';
            if ('' === query) {
                query = 's=';
            }
            if (m.q) {
                query += '&q=' + encodeURIComponent(m.q);
            }
            if (m.session) {
                query += '&x=' + encodeURIComponent(m.session.x);
                query += '&w=' + m.session.w;
            }
            var target = resolveURI(m.URLref, './?' + query);
            http.open(m.op, target, true);
            http.onreadystatechange = function () {
                if (4 !== http.readyState) { return; }
                if (m !== pending.shift()) { throw new Error(); }
                if (0 === pending.length) {
                    active = false;
                } else {
                    ADSAFE.later(output);
                }
                if (m.session) {
                    m.session.w += 1;
                }

                m.resolve(deserialize(m.URLref, http));
            };
            if (undefined === m.argv) {
                http.send(null);
            } else {
                http.setRequestHeader('Content-Type', 'text/plain');
                http.send(serialize(m.argv));
            }

            // TODO: monitor the request with a local timeout
        };
        return function (URLref, op, resolve, q, argv) {
            var session = null;
            if ('POST' === op) {
                var origin = resolveURI(URLref, '/');
                session = ADSAFE.get(sessions, origin);
                if (!session) {
                    session = {};
                    ADSAFE.set(sessions, origin, session);
                    pending.push({
                        URLref: resolveURI(URLref, './#s=sessions'),
                        op: 'POST',
                        q: 'create',
                        argv: [],
                        resolve: function (value) {
                            session.x = value.key;
                            session.w = 1;
                        }
                    });
                }
            }
            pending.push({
                session: session,
                URLref: URLref,
                op: op,
                resolve: resolve,
                q: q,
                argv: argv
            });
            if (!active) {
                ADSAFE.later(output);
                active = true;
            }
        };
    }) ();

    return {

        /**
         * A promise for the target of the current web page.
         */
        page: proxy(window.document.location.toString()),

        /**
         * Constructs a remote promise.
         * @param URLref    URL reference to wrap
         */
        proxy: proxy,

        /**
         * Extracts the URLref contained within a remote promise.
         * @param p remote promise to crack
         * @return the URLref, or <code>null</code> if not a remote promise
         */
        crack: function (p) {
            unsealedURLref = null;
            if ('function' === typeof p) { p(); }
            var r = unsealedURLref;
            unsealedURLref = null;
            return r;
        }
    };
});
