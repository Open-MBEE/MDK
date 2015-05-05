/*******************************************************************************
 * Copyright (c) <2013>, California Institute of Technology ("Caltech").  
 * U.S. Government sponsorship acknowledged.
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are 
 * permitted provided that the following conditions are met:
 * 
 *  - Redistributions of source code must retain the above copyright notice, this list of 
 *    conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice, this list 
 *    of conditions and the following disclaimer in the documentation and/or other materials 
 *    provided with the distribution.
 *  - Neither the name of Caltech nor its operating division, the Jet Propulsion Laboratory, 
 *    nor the names of its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS 
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER  
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package gov.nasa.jpl.mbee.lib;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Manages additions to nested collections based on policy of several
 * attributes.
 * 
 */
public class CollectionAdder {

    public boolean  mustFlatten               = false;
    public boolean  mayFlatten                = false;
    public boolean  flattenIfSizeOne          = false;
    public boolean  flattenToNullIfEmpty      = false;
    public int      defaultFlattenDepth       = 1;
    public boolean  nullOk                    = true;
    public boolean  onlyAddOne                = false;
    public Class<?> unflattenedCollectionType = ArrayList.class;

    /**
     * @param mustFlatten
     * @param mayFlatten
     * @param flattenIfSizeOne
     * @param flattenToNullIfEmpty
     * @param defaultFlattenDepth
     * @param nullOk
     */
    public CollectionAdder(boolean mustFlatten, boolean mayFlatten, boolean flattenIfSizeOne,
            boolean flattenToNullIfEmpty, int defaultFlattenDepth, boolean nullOk, boolean onlyAddOne,
            Class<?> unflattenedCollectionType) {
        this.mustFlatten = mustFlatten;
        this.mayFlatten = mayFlatten;
        this.flattenIfSizeOne = flattenIfSizeOne;
        this.flattenToNullIfEmpty = flattenToNullIfEmpty;
        this.defaultFlattenDepth = defaultFlattenDepth;
        this.nullOk = nullOk;
        this.onlyAddOne = onlyAddOne;
        this.unflattenedCollectionType = unflattenedCollectionType;
    }

    public CollectionAdder() {
    }

    public boolean add(Object o, Collection<Object> list) {
        return add(o, list, defaultFlattenDepth);
    }

    /**
     * Fix an existing Collection according to the ListBuilder's properties.
     * 
     * @param coll
     *            the input Collection
     * @return the original collection or a new instance of
     *         unflattenedCollectionType
     */
    public Object fix(Collection<?> coll) {
        return fix(coll, defaultFlattenDepth);
    }

    /**
     * Fix an existing collection according to the ListBuilder's properties.
     * 
     * @param coll
     * @param flattenDepth
     * @return the original collection or a new unflattenedCollectionType
     *         collection
     */
    @SuppressWarnings("unchecked")
    public Object fix(Collection<?> coll, int flattenDepth) {
        if (!mustFlatten && (!mayFlatten || flattenDepth <= 0))
            return coll;
        Object result = coll;
        if (coll.isEmpty()) {
            if (mustFlatten || flattenToNullIfEmpty) {
                result = null;
            }
        } else if (coll.size() == 1 && flattenIfSizeOne) {
            result = coll.iterator().next();
        } else {
            // flatten!
            // Collection< Object > newList;
            ArrayList<Object> newList = new ArrayList<Object>();
            // try {
            // newList = (Collection< Object
            // >)unflattenedCollectionType.newInstance();
            // } catch ( InstantiationException e ) {
            // newList = new ArrayList<Object>();
            // } catch ( IllegalAccessException e ) {
            // newList = new ArrayList<Object>();
            // }
            result = newList;
            // ListBuilder adder = new ListBuilder( mustFlatten, mayFlatten,
            // flattenIfSizeOne, flattenToNullIfEmpty, flattenDepth-1, nullOk,
            // onlyAddOne, unflattenedCollectionType );
            for (Object child: coll) {
                boolean added = true;
                if (child instanceof Collection) {
                    added = add(child, newList, flattenDepth - 1);
                } else {
                    newList.add(child);
                }
                if (onlyAddOne && added)
                    break;
            }
        }
        // fixType
        if (result instanceof Collection) {
            if (unflattenedCollectionType != null && !unflattenedCollectionType.isInstance(result)) {
                Collection<Object> newColl = null;
                try {
                    Object o = unflattenedCollectionType.newInstance();
                    if (o instanceof Collection) {
                        newColl = (Collection<Object>)o;
                    }
                } catch (InstantiationException e) {
                } catch (IllegalAccessException e) {
                }
                if (newColl != null) {
                    newColl.addAll((Collection<? extends Object>)result);
                    result = newColl;
                }
            }
        }
        return result;
    }

    public boolean add(Object o, Collection<Object> list, int flattenDepth) {
        if (o == null) {
            if (nullOk)
                list.add(null);
            return nullOk;
        }
        if ((o instanceof Collection) && (mustFlatten || (mayFlatten && flattenDepth > 0))) {
            Collection<?> coll = (Collection<?>)o;
            if (coll.isEmpty()) {
                if (mustFlatten || flattenToNullIfEmpty) {
                    add(null, list, flattenDepth - 1);
                    return true;
                }
                return false;
            }
            if (coll.size() != 1 || !flattenIfSizeOne) {
                boolean didAdd = false;
                for (Object child: coll) {
                    boolean added = add(child, list, flattenDepth - 1);
                    if (added) {
                        if (onlyAddOne) {
                            return true;
                        }
                        didAdd = true;
                    }
                }
                return didAdd;
            } // else add unflattened below
            // if ( unflattenedCollectionType != null &&
            // !unflattenedCollectionType.isInstance( coll ) ) {
            // Collection< ? > newColl =
            // unflattenedCollectionType.newInstance();
            // newColl.addAll( (Collection< ? >)coll );
            // return didAdd;
            // }
            // }
        }
        list.add(o);
        return true;
    }
}
