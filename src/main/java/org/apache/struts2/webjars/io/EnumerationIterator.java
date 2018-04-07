/*
 * Copyright (c) 2018 (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.struts2.webjars.io;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Aggregates Enumeration instances into one iterator and filters out duplicates.  Always keeps one
 * ahead of the enumerator to protect against returning duplicates.
 */
public class EnumerationIterator<E> implements Iterator<E> {

    LinkedList<Enumeration<E>> enums = new LinkedList<Enumeration<E>>();
    Enumeration<E> cur = null;
    E next = null;
    Set<E> loaded = new HashSet<E>();

    public EnumerationIterator<E> addEnumeration(Enumeration<E> e) {
        if (e.hasMoreElements()) {
            if (cur == null) {
                cur = e;
                next = e.nextElement();
                loaded.add(next);
            } else {
                enums.add(e);
            }
        }
        return this;
    }

    public boolean hasNext() {
        return (next != null);
    }

    public E next() {
        if (next != null) {
            E prev = next;
            next = loadNext();
            return prev;
        } else {
            throw new NoSuchElementException();
        }
    }

    private Enumeration<E> determineCurrentEnumeration() {
        if (cur != null && !cur.hasMoreElements()) {
            if (enums.size() > 0) {
                cur = enums.removeLast();
            } else {
                cur = null;
            }
        }
        return cur;
    }

    private E loadNext() {
        if (determineCurrentEnumeration() != null) {
            E tmp = cur.nextElement();
            int loadedSize = loaded.size();
            while (loaded.contains(tmp)) {
                tmp = loadNext();
                if (tmp == null || loaded.size() > loadedSize) {
                    break;
                }
            }
            if (tmp != null) {
                loaded.add(tmp);
            }
            return tmp;
        }
        return null;

    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}