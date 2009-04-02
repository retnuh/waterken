// Copyright 2007 Waterken Inc. under the terms of the MIT X license
// found at http://www.opensource.org/licenses/mit-license.html
package org.waterken.thread;

import java.util.LinkedList;

import org.ref_send.promise.Promise;
import org.ref_send.promise.Receiver;

/**
 * A concurrent loop.
 */
public final class
Concurrent {
    private Concurrent() {}

    /**
     * Constructs an instance.
     * @param group thread group
     * @param name  thread name
     */
    static public <T extends Promise<?>> Receiver<T>
    make(final ThreadGroup group, final String name) {
        if (null == group) { throw new NullPointerException(); }
        if (null == name) { throw new NullPointerException(); }

        final LinkedList<T> tasks = new LinkedList<T>();        
        return new Receiver<T>() {
            private boolean running = false;

            public synchronized void
            run(final T task) {
                if (null == task) { throw new NullPointerException(); }
                
                tasks.add(task);
                if (!running) {
                    final Object lock = this;
                    new Thread(group, name) {
                        public void
                        run() {
                            System.out.println(this + ": processing...");
                            try {
                                while (true) {
                                    final T todo;
                                    synchronized (lock) {
                                        if (tasks.isEmpty()) {
                                            running = false;
                                            break;
                                        }
                                        todo = tasks.removeFirst();
                                    }
                                    try {
                                        todo.call();
                                    } catch (final Exception e) {
                                        System.err.println(this + ":");
                                        e.printStackTrace(System.err);
                                    }
                                }
                            } catch (final Throwable e) {
                                System.err.println(this + ":");
                                e.printStackTrace(System.err);
                            }
                            System.out.println(this + ": idling...");
                        }
                    }.start();
                    running = true;
                }
            }
        };
    }
}
