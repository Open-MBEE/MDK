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
package gov.nasa.jpl.mbee.dgview.impl;

import gov.nasa.jpl.mbee.dgview.ColSpec;
import gov.nasa.jpl.mbee.dgview.DgviewPackage;
import gov.nasa.jpl.mbee.dgview.Table;
import gov.nasa.jpl.mbee.dgview.TableRow;

import java.util.Collection;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.util.EObjectContainmentEList;
import org.eclipse.emf.ecore.util.InternalEList;

/**
 * <!-- begin-user-doc --> An implementation of the model object '
 * <em><b>Table</b></em>'. <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * <ul>
 * <li>{@link gov.nasa.jpl.mbee.dgview.impl.TableImpl#getBody <em>
 * Body</em>}</li>
 * <li>{@link gov.nasa.jpl.mbee.dgview.impl.TableImpl#getCaption
 * <em>Caption</em>}</li>
 * <li>{@link gov.nasa.jpl.mbee.dgview.impl.TableImpl#getStyle <em>
 * Style</em>}</li>
 * <li>{@link gov.nasa.jpl.mbee.dgview.impl.TableImpl#getHeaders
 * <em>Headers</em>}</li>
 * <li>{@link gov.nasa.jpl.mbee.dgview.impl.TableImpl#getColspecs
 * <em>Colspecs</em>}</li>
 * <li>{@link gov.nasa.jpl.mbee.dgview.impl.TableImpl#getCols <em>
 * Cols</em>}</li>
 * </ul>
 * </p>
 * 
 * @generated
 */
public class TableImpl extends ViewElementImpl implements Table {
    /**
     * The cached value of the '{@link #getBody() <em>Body</em>}' containment
     * reference list. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getBody()
     * @generated
     * @ordered
     */
    protected EList<TableRow>     body;

    /**
     * The default value of the '{@link #getCaption() <em>Caption</em>}'
     * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getCaption()
     * @generated
     * @ordered
     */
    protected static final String CAPTION_EDEFAULT = null;

    /**
     * The cached value of the '{@link #getCaption() <em>Caption</em>}'
     * attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getCaption()
     * @generated
     * @ordered
     */
    protected String              caption          = CAPTION_EDEFAULT;

    /**
     * The default value of the '{@link #getStyle() <em>Style</em>}' attribute.
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getStyle()
     * @generated
     * @ordered
     */
    protected static final String STYLE_EDEFAULT   = null;

    /**
     * The cached value of the '{@link #getStyle() <em>Style</em>}' attribute.
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getStyle()
     * @generated
     * @ordered
     */
    protected String              style            = STYLE_EDEFAULT;

    /**
     * The cached value of the '{@link #getHeaders() <em>Headers</em>}'
     * containment reference list. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getHeaders()
     * @generated
     * @ordered
     */
    protected EList<TableRow>     headers;

    /**
     * The cached value of the '{@link #getColspecs() <em>Colspecs</em>}'
     * containment reference list. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getColspecs()
     * @generated
     * @ordered
     */
    protected EList<ColSpec>      colspecs;

    /**
     * The default value of the '{@link #getCols() <em>Cols</em>}' attribute.
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getCols()
     * @generated
     * @ordered
     */
    protected static final int    COLS_EDEFAULT    = 0;

    /**
     * The cached value of the '{@link #getCols() <em>Cols</em>}' attribute.
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @see #getCols()
     * @generated
     * @ordered
     */
    protected int                 cols             = COLS_EDEFAULT;

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    protected TableImpl() {
        super();
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    protected EClass eStaticClass() {
        return DgviewPackage.Literals.TABLE;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public EList<TableRow> getBody() {
        if (body == null) {
            body = new EObjectContainmentEList<TableRow>(TableRow.class, this, DgviewPackage.TABLE__BODY);
        }
        return body;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public String getCaption() {
        return caption;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public void setCaption(String newCaption) {
        String oldCaption = caption;
        caption = newCaption;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, DgviewPackage.TABLE__CAPTION, oldCaption,
                    caption));
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public String getStyle() {
        return style;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public void setStyle(String newStyle) {
        String oldStyle = style;
        style = newStyle;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, DgviewPackage.TABLE__STYLE, oldStyle, style));
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public EList<TableRow> getHeaders() {
        if (headers == null) {
            headers = new EObjectContainmentEList<TableRow>(TableRow.class, this,
                    DgviewPackage.TABLE__HEADERS);
        }
        return headers;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public EList<ColSpec> getColspecs() {
        if (colspecs == null) {
            colspecs = new EObjectContainmentEList<ColSpec>(ColSpec.class, this,
                    DgviewPackage.TABLE__COLSPECS);
        }
        return colspecs;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public int getCols() {
        return cols;
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public void setCols(int newCols) {
        int oldCols = cols;
        cols = newCols;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, DgviewPackage.TABLE__COLS, oldCols, cols));
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
        switch (featureID) {
            case DgviewPackage.TABLE__BODY:
                return ((InternalEList<?>)getBody()).basicRemove(otherEnd, msgs);
            case DgviewPackage.TABLE__HEADERS:
                return ((InternalEList<?>)getHeaders()).basicRemove(otherEnd, msgs);
            case DgviewPackage.TABLE__COLSPECS:
                return ((InternalEList<?>)getColspecs()).basicRemove(otherEnd, msgs);
        }
        return super.eInverseRemove(otherEnd, featureID, msgs);
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public Object eGet(int featureID, boolean resolve, boolean coreType) {
        switch (featureID) {
            case DgviewPackage.TABLE__BODY:
                return getBody();
            case DgviewPackage.TABLE__CAPTION:
                return getCaption();
            case DgviewPackage.TABLE__STYLE:
                return getStyle();
            case DgviewPackage.TABLE__HEADERS:
                return getHeaders();
            case DgviewPackage.TABLE__COLSPECS:
                return getColspecs();
            case DgviewPackage.TABLE__COLS:
                return getCols();
        }
        return super.eGet(featureID, resolve, coreType);
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @SuppressWarnings("unchecked")
    @Override
    public void eSet(int featureID, Object newValue) {
        switch (featureID) {
            case DgviewPackage.TABLE__BODY:
                getBody().clear();
                getBody().addAll((Collection<? extends TableRow>)newValue);
                return;
            case DgviewPackage.TABLE__CAPTION:
                setCaption((String)newValue);
                return;
            case DgviewPackage.TABLE__STYLE:
                setStyle((String)newValue);
                return;
            case DgviewPackage.TABLE__HEADERS:
                getHeaders().clear();
                getHeaders().addAll((Collection<? extends TableRow>)newValue);
                return;
            case DgviewPackage.TABLE__COLSPECS:
                getColspecs().clear();
                getColspecs().addAll((Collection<? extends ColSpec>)newValue);
                return;
            case DgviewPackage.TABLE__COLS:
                setCols((Integer)newValue);
                return;
        }
        super.eSet(featureID, newValue);
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public void eUnset(int featureID) {
        switch (featureID) {
            case DgviewPackage.TABLE__BODY:
                getBody().clear();
                return;
            case DgviewPackage.TABLE__CAPTION:
                setCaption(CAPTION_EDEFAULT);
                return;
            case DgviewPackage.TABLE__STYLE:
                setStyle(STYLE_EDEFAULT);
                return;
            case DgviewPackage.TABLE__HEADERS:
                getHeaders().clear();
                return;
            case DgviewPackage.TABLE__COLSPECS:
                getColspecs().clear();
                return;
            case DgviewPackage.TABLE__COLS:
                setCols(COLS_EDEFAULT);
                return;
        }
        super.eUnset(featureID);
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public boolean eIsSet(int featureID) {
        switch (featureID) {
            case DgviewPackage.TABLE__BODY:
                return body != null && !body.isEmpty();
            case DgviewPackage.TABLE__CAPTION:
                return CAPTION_EDEFAULT == null ? caption != null : !CAPTION_EDEFAULT.equals(caption);
            case DgviewPackage.TABLE__STYLE:
                return STYLE_EDEFAULT == null ? style != null : !STYLE_EDEFAULT.equals(style);
            case DgviewPackage.TABLE__HEADERS:
                return headers != null && !headers.isEmpty();
            case DgviewPackage.TABLE__COLSPECS:
                return colspecs != null && !colspecs.isEmpty();
            case DgviewPackage.TABLE__COLS:
                return cols != COLS_EDEFAULT;
        }
        return super.eIsSet(featureID);
    }

    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    @Override
    public String toString() {
        if (eIsProxy())
            return super.toString();

        StringBuffer result = new StringBuffer(super.toString());
        result.append(" (caption: ");
        result.append(caption);
        result.append(", style: ");
        result.append(style);
        result.append(", cols: ");
        result.append(cols);
        result.append(')');
        return result.toString();
    }

} // TableImpl
