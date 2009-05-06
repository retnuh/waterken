/*
 * Copyright 2007-2009 Tyler Close under the terms of the MIT X license found
 * at http://www.opensource.org/licenses/mit-license.html
 *
 * web_send.js version: 2009-05-05
 *
 * This library doesn't actually pass the ADsafe verifier, but rather is
 * designed to provide a controlled interface to the network, that can be
 * loaded as an ADsafe library. Clients of this library have permission to send
 * requests to the window target, and any target returned in a request
 * response.  ADsafe verified clients *cannot* construct a remote reference
 * from whole cloth by providing a URL. In this way, a server can control the
 * client's network access by controlling what remote references are made
 * available to the client.
 *
 * In addition to messaging, the client is also permitted to read/write the
 * window title and navigate the window to any received remote target.
 */
"use strict";
ADSAFE.lib('web', function (lib) {

    /**
     * initial number of milliseconds to wait before retrying a request
     */
    var initialTimeout = 15 * 1000;

    /**
     * Does an object define a given key?
     */
    function includes(map, key) {
        return map && Object.hasOwnProperty.call(map, key);
    }

    /**
     * secret slot to extract the URL from a remote reference
     * <p>
     * Invoking a remote reference puts the URL in the slot.
     * </p>
     */
    var unsealedURLref = null;

    /**
     * value returned on a 404 server response
     */
    var notYetPumpkin = lib.Q.reject({
        $: [ 'org.ref_send.promise.Failure', 'NaO' ],
        status: 404,
        phrase: 'Not Found'
    });

    /**
     * Constructs a remote reference.
     * @param target    absolute URLref for target resource
     */
    var sealURLref = (function () {
        var pendingRemotePromises = { /* URLref => local promise */ };
        return function (target) {
            var self = function (op, arg1, arg2, arg3) {
                if (void 0 === op) {
                    unsealedURLref = target;
                    return self;
                }
                if (/#o=/.test(target)) {
                    var local = ADSAFE.get(pendingRemotePromises, target);
                    if (!local) {
                        var pr = lib.Q.defer();
                        local = pr.promise;
                        ADSAFE.set(pendingRemotePromises, target, local);
                        var timeout = initialTimeout;
                        var retry = function (x) {
                            if (notYetPumpkin === x) {
                                ADSAFE.later(function () {
                                    send(target, 'GET', retry);
                                }, timeout);
                                timeout = Math.min(2*timeout, 60*60*1000);
                            } else {
                                delete pendingRemotePromises[target];
                                pr.resolve(x);
                            }
                        };
                        send(target, 'GET', retry);
                    }
                    local(op, arg1, arg2, arg3);
                } else {
                    if ('WHEN' === op) {
                        arg1(self);
                    } else {
                        send(target, op, arg1, arg2, arg3);
                    }
                }
            };
            return self;
        };
    }) ();

    /**
     * Produces a relative URL reference.
     * @param base  absolute base URLref
     * @param href  absolute target URLref
     */
    function relateURI(base, href) {
        var baseOP  = /^([a-zA-Z][\w\-\.\+]*:\/\/[^\/]*\/)([^\?#]*)/.exec(base);
        var hrefOPR = /^([a-zA-Z][\w\-\.\+]*:\/\/[^\/]*\/)([^\?#]*)(.*)$/.
                                                                     exec(href);
        if (!baseOP || !hrefOPR || baseOP[1] !== hrefOPR[1]) { return href; }

        // determine the common parent folder
        var basePath = baseOP[2].split('/');
        var hrefPath = hrefOPR[2].split('/');
        var maxMatch = Math.min(basePath.length, hrefPath.length) - 1;
        var i = 0;
        while (i !== maxMatch && basePath[i] === hrefPath[i]) { ++i; }

        // wind up to the common parent folder
        var cd = '';
        for (var n = basePath.length - i - 1; 0 !== n--;) { cd += '../'; }
        if ('' === cd) {
            cd = './';
        }
        return cd + hrefPath.slice(i).join('/') + hrefOPR[3];
    }

    /**
     * Produce the JSON text for a JSON value.
     * @param base  absolute base URLref
     * @param arg   JSON value to serialize
     */
    function serialize(base, arg) {
        if (null === arg || 'object' !== typeof arg) {
            arg = { '=' : arg };
        }
        return JSON.stringify(arg, function (key, value) {
            switch (typeof value) {
            case 'function':
                unsealedURLref = null;
                value = value();
                if (null !== unsealedURLref) {
                    value = { '@' : relateURI(base, unsealedURLref) };
                }
                unsealedURLref = null;
                break;
            case 'number':
                if (!isFinite(value)) {
                    value = { '!' : { $: [ 'NaN' ] } };
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
        if (/^[a-zA-Z][\w\-\.\+]*:/.test(href)) { return href; }

        base = /^[^#]*/.exec(base)[0];  // never include base fragment
        if ('' === href) { return base; }
        if (/^#/.test(href)) { return base + href; }
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
        var baseOR = /^([a-zA-Z][\w\-\.\+]*:\/\/[^\/]*\/)(.*)$/.exec(base);
        var host = baseOR[1];
        var path = baseOR[2];
        while (true) {
            if (/^\.\.\//.test(href)) {
                path = path.substring(0, path.lastIndexOf('/',path.length-2)+1);
                href = href.substring('../'.length);
            } else if (/^\.\//.test(href)) {
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
                if (includes(value, '!')) { return lib.Q.reject(value['!']); }
                if (includes(value, '@')) {
                    return sealURLref(resolveURI(base, value['@']));
                }
                if (includes(value, '=')) { return value['=']; }
                return value;
            });
        case 204:
        case 205:
            return null;
        case 303:
            var see = http.getResponseHeader('Location');
            return see ? sealURLref(resolveURI(base, see)) : null;
        case 404:
            return notYetPumpkin;
        default:
            return lib.Q.reject({
                $: [ 'org.ref_send.promise.Failure', 'NaO' ],
                status: http.status,
                phrase: http.statusText
            });
        }
    }

    /**
     * Constructs a Request-URI for a web-key with options.
     * @param target    target URLref
     * @param q         optional client-specified query
     * @param x         optional session key
     * @param w         optional message window number
     */
    function makeRequestURI(target, q, x, w) {
        var requestQuery = '';
        if (void 0 !== q && null !== q) {
            requestQuery = '?q=' + encodeURIComponent(String(q));
        }
        if (x) {
            requestQuery += '' === requestQuery ? '?' : '&';
            requestQuery += 'x=' + encodeURIComponent(String(x));
            requestQuery += '&w=' + encodeURIComponent(String(w));
        }
        var pqf = /([^\?#]*)([^#]*)(.*)/.exec(target);
        if (pqf[2]) {
            requestQuery += '' === requestQuery ? '?' : '&';
            requestQuery += pqf[2].substring(1);
        }
        if (pqf[3]) {
            requestQuery += '' === requestQuery ? '?' : '&';
            requestQuery += pqf[3].substring(1);
        }
        return pqf[1] + requestQuery;
    }

    /**
     * Constructs a pending request queue.
     */
    function makeSession() {
        var x = null;               // session id
        var w = 0;                  // number of received responses
        var pending = [];           // pending requests
        var initialized = false;    // session initialization request queued?
        var connection = null;      // current connection

        function makeConnection(timeout) {
            if (void 0 === timeout) {
                timeout = initialTimeout;
            }
            var http;
            if (window.XMLHttpRequest) {
                http = new XMLHttpRequest();
            } else {
                http = new ActiveXObject('Microsoft.XMLHTTP');
            }
            var heartbeat = (new Date()).getTime();
            var self = function () {
                if (self !== connection) { return; }

                var m = pending[0];
                var requestURI = makeRequestURI(
                    m.target, m.q, m.idempotent ? null : x, w);
                http.open(m.op, requestURI, true);
                http.onreadystatechange = function () {
                    if (3 === http.readyState || 4 === http.readyState) {
                        heartbeat = (new Date()).getTime();
                    }
                    if (self !== connection) { return; }

                    if (4 !== http.readyState) { return; }
                    if (http.status < 200 || http.status >= 500) { return; }

                    if (m !== pending.shift()) { throw new Error(); }
                    w += 1;
                    if (0 === pending.length) {
                        connection = null;
                    } else {
                        ADSAFE.later(self);
                    }

                    m.resolve(deserialize(requestURI, http));
                };
                if (void 0 === m.argv) {
                    http.send(null);
                } else {
                    http.setRequestHeader('Content-Type', 'text/plain');
                    http.send(serialize(requestURI, m.argv));
                }
            };
            if (timeout) { (function () {
                var watcher = function () {
                    if (connection !== self) { return; }

                    var delta = ((new Date()).getTime()) - heartbeat;
                    if (delta >= timeout) {
                        if (x || pending[0].idempotent) {
                            connection = makeConnection(
                                Math.min(2 * timeout, 60 * 60 * 1000));
                            ADSAFE.later(connection);
                            if ('function' === http.abort) { http.abort(); }
                        }
                    } else {
                        ADSAFE.later(watcher, timeout - delta);
                    }
                };
                ADSAFE.later(watcher, timeout);
            }) (); }
            return self;
        }

        return function (target, op, resolve, q, argv) {
            var idempotent = 'GET' === op || 'HEAD' === op ||
                             'PUT' === op || 'DELETE' === op ||
                             'OPTIONS' === op || 'TRACE' === op;
            if (!idempotent && !initialized) {
                pending.push({
                    idempotent: true,
                    target: resolveURI(target, '?q=create&s=sessions'),
                    op: 'POST',
                    argv: [],
                    resolve: function (value) {
                        x = value.key;
                    }
                });
                initialized = true;
            }
            pending.push({
                idempotent: idempotent,
                target: target,
                op: op,
                resolve: resolve,
                q: q,
                argv: argv
            });
            if (!connection) {
                connection = makeConnection();
                ADSAFE.later(connection);
            }
        };
    }

    /**
     * Enqueues an HTTP request.
     * @param target    target URLref
     * @param op        HTTP verb
     * @param resolve   response resolver
     * @param q         query string argument
     * @param argv      JSON value for request body
     */
    var send = (function () {
        var sessions = { /* origin => session */ };
        return function (target, op, resolve, q, argv) {
            var origin = resolveURI(target, '/');
            var session = ADSAFE.get(sessions, origin);
            if (!session) {
                session = makeSession();
                ADSAFE.set(sessions, origin, session);
            }
            return session(target, op, resolve, q, argv);
        };
    }) ();

    function unsealURLref(p) {
        unsealedURLref = null;
        if ('function' === typeof p) { p(); }
        var r = unsealedURLref;
        unsealedURLref = null;
        return r;
    }

    return {

        /**
         * Gets a remote reference for the window's current location.
         */
        getLocation: function () { return sealURLref(window.location.href); },

        /**
         * Navigate the window.
         * @param target    remote reference for new location
         * @return <code>true</code> if navigation successful,
         *         else <code>false</code>
         */
        navigate: function (target) {
            var href = unsealURLref(target);
            if (null === href) { return false; }
            window.location.assign(href);
            return true;
        },

        /**
         * Sets the 'href' attribute.
         * @param elements  bunch of elements to modify
         * @param target    remote reference
         * @return number of elements modified
         */
        href: function (elements, target) {
            var n = 0;
            if (null === target) {
                elements.___nodes___.filter(function (node) {
                    node.removeAttribute('href');
                    node.onclick = void 0;
                    n += 1;
                });
            } else {
                var href = unsealURLref(target);
                if (null !== href) {
                    elements.___nodes___.filter(function (node) {
                        switch (node.tagName.toUpperCase()) {
                        case 'A':
                            node.setAttribute('href', href);

                            // navigate even if fragment is only difference
                            node.onclick = function () {
                                // TODO: do original fragment navigation
                                window.location.assign(href);
                            };

                            n += 1;
                            break;
                        }
                    });
                }
            }
            return n;
        },

        /**
         * Sets the 'src' attribute.
         * @param elements  bunch of elements to modify
         * @param target    remote reference
         * @return number of elements modified
         */
        src: function (img, target) {
            var n = 0;
            if (null === target) {
                elements.___nodes___.filter(function (node) {
                    node.removeAttribute('src');
                    n += 1;
                });
            } else {
                var src = unsealURLref(target);
                if (null !== src) {
                    elements.___nodes___.filter(function (node) {
                        switch (node.tagName.toUpperCase()) {
                        case 'IMG':
                        case 'INPUT':
                            node.setAttribute('src', makeRequestURI(src));
                            n += 1;
                            break;
                        }
                    });
                }
            }
            return n;
        },

        /**
         * Gets the document title.
         */
        getTitle: function () { return window.document.title; },

        /**
         * Sets the document title.
         * @param text  new title text
         */
        setTitle: function (text) { window.document.title = text; },

        // Non-ADsafe API

        /**
         * Constructs a remote reference.
         * @param base  optional remote reference for base URLref
         * @param href  URLref to wrap
         * @param args  optional query argument map
         */
        _ref: function (base, href, args) {
            var url = resolveURI(unsealURLref(base), href);
            if (void 0 !== args && null !== args) {
                var query = '?';
                if ('object' === typeof args) {
                    for (k in args) { if (includes(args, k)) {
                        if ('?' !== query) {
                            query += '&';
                        }
                        query += encodeURIComponent(String(k)) + '=' +
                                 encodeURIComponent(String(ADSAFE.get(args,k)));
                    } }
                } else {
                    query += args;
                }
                url = resolveURI(url, query);
            }
            return sealURLref(url);
        },

        /**
         * Extracts the URLref contained within a remote reference.
         * @param arg       remote reference to extract URLref from
         * @param target    optional remote reference for base URL
         * @return the URLref, or <code>null</code> if not a remote reference
         */
        _url: function (arg, target) {
            var href = unsealURLref(arg);
            if (null === href || !target) { return href; }
            var base = unsealURLref(target);
            if (null === base) { return href; }
            return relateURI(base, href);
        }
    };
});
