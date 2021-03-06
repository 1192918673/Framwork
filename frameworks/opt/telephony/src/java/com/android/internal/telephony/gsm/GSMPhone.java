/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.gsm;

/* M: SS part */
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import com.android.internal.telephony.TelephonyIntents;
/* M: SS part end */
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.VideoProfile;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SubInfoRecord;
import android.telephony.TelephonyManager;

import com.android.ims.ImsManager;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import android.text.TextUtils;
import android.telephony.Rlog;

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION_2;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.dataconnection.DcSwitchState;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;

import android.telephony.SubscriptionManager;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;


import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.android.internal.telephony.PhoneConstants.EVENT_SUBSCRIPTION_ACTIVATED;
import static com.android.internal.telephony.PhoneConstants.EVENT_SUBSCRIPTION_DEACTIVATED;

import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.NetworkInfoWithAcT;
import com.android.internal.telephony.OperatorInfo;

/**
 * {@hide}
 */
public class GSMPhone extends PhoneBase {
    // NOTE that LOG_TAG here is "GSM", which means that log messages
    // from this file will go into the radio log rather than the main
    // log.  (Use "adb logcat -b radio" to see them.)
    static final String LOG_TAG = "GSMPhone";
    private static final boolean LOCAL_DEBUG = true;
    private static final boolean VDBG = false; /* STOPSHIP if true */

    // Key used to read/write current ciphering state
    public static final String CIPHERING_KEY = "ciphering_key";
    // Key used to read/write voice mail number
    public static final String VM_NUMBER = "vm_number_key";
    // Key used to read/write the SIM IMSI used for storing the voice mail
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";

    // Instance Variables
    GsmCallTracker mCT;
    GsmServiceStateTracker mSST;
    ArrayList <GsmMmiCode> mPendingMMIs = new ArrayList<GsmMmiCode>();
    SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    PhoneSubInfo mSubInfo;


    Registrant mPostDialHandler;

    /** List of Registrants to receive Supplementary Service Notifications. */
    RegistrantList mSsnRegistrants = new RegistrantList();

    // mEcmTimerResetRegistrants are informed after Ecm timer is canceled or re-started
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();

    private String mImei;
    private String mImeiSv;
    private String mVmNumber;

    /**
        * mImeiAbnormal=0, Valid IMEI
        * mImeiAbnormal=1, IMEI is null or not valid format
        * mImeiAbnormal=2, Phone1/Phone2 have same IMEI
        */
    private int mImeiAbnormal = 0;

    /* M: SS part */
    private static final String CFU_QUERY_ICCID_PROP = "persist.radio.cfu.iccid.";
    private static final String CFU_QUERY_SIM_CHANGED_PROP = "persist.radio.cfu.change.";
    /* M: SS part end */

    private IsimUiccRecords mIsimUiccRecords;

    /* M: call control part start */
    /** List of Registrants to receive CRSS Notifications. */
    RegistrantList mCallRelatedSuppSvcRegistrants = new RegistrantList();
    /* M: call control part end */

    /* M: SS part */
    private boolean needQueryCfu = true;

    /* To solve [ALPS00455020]No CFU icon showed on status bar. */
    private boolean mIsCfuRegistered = false;

    /* For solving ALPS01023811
       To determine if CFU query is for power-on query.
    */
    private int mCfuQueryRetryCount = 0;
    private static final String CFU_QUERY_PROPERTY_NAME = "gsm.poweron.cfu.query.";
    private static final int cfuQueryWaitTime = 1000;
    private static final int CFU_QUERY_MAX_COUNT = 60;
    /* M: SS part end */


    /* M: Network part */
    public static final String UTRAN_INDICATOR = "3G";
    public static final String LTE_INDICATOR = "4G";
    public static final String ACT_TYPE_GSM = "0";
    public static final String ACT_TYPE_UTRAN = "2";
    public static final String ACT_TYPE_LTE = "7";

    // IMS registration
    private boolean mImsStatus = false;
    private String mImsExtInfo;
    /* M: Network part end */

    //M: For Plmn search
    private static final int PLMN_LIST_STEP_DONE = 0;
    private static final int PLMN_LIST_STEP_PS_DETACH = 1;
    private static final int PLMN_LIST_STEP_VIRTUAL_MODE = 2;
    private static final int PLMN_LIST_STEP_SEARCH = 3;
    private int mAvailableNetworkStep = PLMN_LIST_STEP_DONE;
    private Message mAvailableNetworkMsg = null;

    // Create Cfu (Call forward unconditional) so that dialling number &
    // mOnComplete (Message object passed by client) can be packed &
    // given as a single Cfu object as user data to RIL.
    private static class Cfu {
        final String mSetCfNumber;
        final Message mOnComplete;

        Cfu(String cfNumber, Message onComplete) {
            mSetCfNumber = cfNumber;
            mOnComplete = onComplete;
        }
    }

    // Constructors

    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context,ci,notifier, false);
    }

    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("GSM", notifier, context, ci, unitTestMode);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        mCT = new GsmCallTracker(this);

        mSST = new GsmServiceStateTracker(this);
        mDcTracker = new DcTracker(this);

        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        /* M: call control part start */
        mCT.registerForVoiceCallIncomingIndication(this, EVENT_VOICE_CALL_INCOMING_INDICATION, null);
        /* M: call control part end */
        setProperties();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        context.registerReceiver(mBroadcastReceiver, filter);
     }

    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public
    GSMPhone (Context context, CommandsInterface ci,
            PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super("GSM", notifier, context, ci, unitTestMode, phoneId);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        mCT = new GsmCallTracker(this);

        mSST = new GsmServiceStateTracker(this);
        mDcTracker = new DcTracker(this);

        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        /* M: call control part start */
        mCT.registerForVoiceCallIncomingIndication(this, EVENT_VOICE_CALL_INCOMING_INDICATION, null);
        /* M: call control part end */

        /* M: SS part */
        /* register for CRSS Notification */
        mCi.setOnCallRelatedSuppSvc(this, EVENT_CRSS_IND, null);
        /* M: SS part end */

        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        context.registerReceiver(mBroadcastReceiver, filter);

        setProperties();

        log("GSMPhone: constructor: sub = " + mPhoneId);

        setProperties();
    }

    protected void setProperties() {
        TelephonyManager.setTelephonyProperty(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                getSubId(), new Integer(PhoneConstants.PHONE_TYPE_GSM).toString());
    }

    @Override
    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();

            //Unregister from all former registered events
            mCi.unregisterForAvailable(this); //EVENT_RADIO_AVAILABLE
            unregisterForSimRecordEvents();
            mCi.unregisterForOffOrNotAvailable(this); //EVENT_RADIO_OFF_OR_NOT_AVAILABLE
            mCi.unregisterForOn(this); //EVENT_RADIO_ON
            mSST.unregisterForNetworkAttached(this); //EVENT_REGISTERED_TO_NETWORK
            mCi.unSetOnUSSD(this);
            mCi.unSetOnSuppServiceNotification(this);

            /** M: for suspend data during plmn list */
            mCi.unregisterForGetAvailableNetworksDone(this);
            mSST.unregisterForDataConnectionDetached(this);

            mPendingMMIs.clear();

            //Force all referenced classes to unregister their former registered events
            mCT.dispose();
            mDcTracker.dispose();
            mSST.dispose();
            mSimPhoneBookIntManager.dispose();
            mSubInfo.dispose();
        }
    }

    @Override
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        mSimulatedRadioControl = null;
        mSimPhoneBookIntManager = null;
        mSubInfo = null;
        mCT = null;
        mSST = null;

        super.removeReferences();
    }

    @Override
    protected void finalize() {
        if(LOCAL_DEBUG) Rlog.d(LOG_TAG, "GSMPhone finalized");
    }


    private void onSubscriptionActivated() {
        //mSubscriptionData = SubscriptionManager.getCurrentSubscription(mSubscription);

        log("SUBSCRIPTION ACTIVATED : slotId : " + mSubscriptionData.slotId
                + " appid : " + mSubscriptionData.m3gppIndex
                + " subId : " + mSubscriptionData.subId
                + " subStatus : " + mSubscriptionData.subStatus);

        // Make sure properties are set for proper subscription.
        setProperties();

        onUpdateIccAvailability();
        mSST.sendMessage(mSST.obtainMessage(ServiceStateTracker.EVENT_ICC_CHANGED));
        ((DcTracker)mDcTracker).updateRecords();
    }

    private void onSubscriptionDeactivated() {
        log("SUBSCRIPTION DEACTIVATED");
        mSubscriptionData = null;
        resetSubSpecifics();
    }

    @Override
    public ServiceState
    getServiceState() {
        if (mSST == null || mSST.mSS.getState() != ServiceState.STATE_IN_SERVICE) {
            if (mImsPhone != null &&
                    mImsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
                return mImsPhone.getServiceState();
            }
        }

        if (mSST != null) {
            return mSST.mSS;
        } else {
            // avoid potential NPE in EmergencyCallHelper during Phone switch
            return new ServiceState();
        }
    }

    @Override
    public CellLocation getCellLocation() {
        return mSST.getCellLocation();
    }

    @Override
    public PhoneConstants.State getState() {
        return mCT.mState;
    }

    @Override
    public int getPhoneType() {
        return PhoneConstants.PHONE_TYPE_GSM;
    }

    @Override
    public ServiceStateTracker getServiceStateTracker() {
        return mSST;
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }

    @Override
    public List<? extends MmiCode>
    getPendingMmiCodes() {
        /* M: SS part */
        Rlog.d(LOG_TAG, "mPendingMMIs.size() = " + mPendingMMIs.size());
        /* M: SS part end */
        return mPendingMMIs;
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;

        //MTK-START [ALPS00093395] Temporary solution to avoid apnType NullException
        if (apnType == null) {
            apnType = "";
        }
        //MTK-END[ALPS00093395] Temporary solution to avoid apnType NullException

        if (mSST == null) {
            // Radio Technology Change is ongoning, dispose() and removeReferences() have
            // already been called

            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (!apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY) &&
                mSST.getCurrentDataConnectionState() != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow

            // Emergency APN is available even in Out Of Service
            // Pass the actual State of EPDN

            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (mDcTracker.isApnTypeEnabled(apnType) == false ||
                mDcTracker.isApnTypeActive(apnType) == false) {
            //TODO: isApnTypeActive() is just checking whether ApnContext holds
            //      Dataconnection or not. Checking each ApnState below should
            //      provide the same state. Calling isApnTypeActive() can be removed.
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else { /* mSST.gprsState == ServiceState.STATE_IN_SERVICE */
            switch (mDcTracker.getState(apnType)) {
                case RETRYING:
                    //M: ALPS01285188
                    if (PhoneConstants.APN_TYPE_MMS.equals(apnType)) {
                        log("mms is retrying!!");
                        ret = PhoneConstants.DataState.CONNECTING;
                        break;
                    }
                case FAILED:
                case IDLE:
                    ret = PhoneConstants.DataState.DISCONNECTED;
                break;

                case CONNECTED:
                case DISCONNECTING:
                    if ( mCT.mState != PhoneConstants.State.IDLE
                            && !mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    } else {
                        ret = PhoneConstants.DataState.CONNECTED;
                    }

                    // M: check peer phone is in call also
                    int phoneCount = TelephonyManager.getDefault().getPhoneCount();
                    if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                        for (int i = 0; i < phoneCount; i++) {
                            PhoneBase pb = (PhoneBase) ((PhoneProxy) PhoneFactory.getPhone(i))
                                    .getActivePhone();

                            if (pb != null && i != getPhoneId() &&
                                    pb.getState() != PhoneConstants.State.IDLE) {
                                Rlog.d(LOG_TAG, "GSMPhone[" + getPhoneId() + "] Phone" + i +
                                        " is in call");

                                ret = PhoneConstants.DataState.SUSPENDED;
                                break;
                            }
                        }
                    }

                    //ALPS01454896: If default data is disable, and current state is disconnecting
                    //we don't have to show the data icon.
                    boolean isUserDataEnabled = Settings.Global.getInt(
                        getContext().getContentResolver(), Settings.Global.MOBILE_DATA, 1) == 1;
                    if (ret == PhoneConstants.DataState.CONNECTED &&
                                apnType == PhoneConstants.APN_TYPE_DEFAULT &&
                                mDcTracker.getState(apnType) == DctConstants.State.DISCONNECTING &&
                                !isUserDataEnabled) {
                        log("Connected but default data is not open.");
                        ret = PhoneConstants.DataState.DISCONNECTED;
                    }
                break;

                case CONNECTING:
                case SCANNING:
                    ret = PhoneConstants.DataState.CONNECTING;
                break;
            }
        }
        return ret;
    }

    @Override
    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;

        if (mSST.getCurrentDataConnectionState() == ServiceState.STATE_IN_SERVICE) {
            switch (mDcTracker.getActivity()) {
                case DATAIN:
                    ret = DataActivityState.DATAIN;
                break;

                case DATAOUT:
                    ret = DataActivityState.DATAOUT;
                break;

                case DATAINANDOUT:
                    ret = DataActivityState.DATAINANDOUT;
                break;

                case DORMANT:
                    ret = DataActivityState.DORMANT;
                break;

                default:
                    ret = DataActivityState.NONE;
                break;
            }
        }

        return ret;
    }

    /**
     * Notify any interested party of a Phone state change
     * {@link com.android.internal.telephony.PhoneConstants.State}
     */
    /*package*/ void notifyPhoneStateChanged() {
        mNotifier.notifyPhoneState(this);
    }

    /**
     * Notify registrants of a change in the call state. This notifies changes in
     * {@link com.android.internal.telephony.Call.State}. Use this when changes
     * in the precise call state are needed, else use notifyPhoneStateChanged.
     */
    /*package*/ void notifyPreciseCallStateChanged() {
        /* we'd love it if this was package-scoped*/
        super.notifyPreciseCallStateChangedP();
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    /*package*/ void
    notifyDisconnect(Connection cn) {
        mDisconnectRegistrants.notifyResult(cn);

        mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    void notifyUnknownConnection(Connection cn) {
        mUnknownConnectionRegistrants.notifyResult(cn);
    }

    void notifySuppServiceFailed(SuppService code) {
        mSuppServiceFailedRegistrants.notifyResult(code);
    }

    /*package*/ void
    notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    /*package*/
    void notifyLocationChanged() {
        mNotifier.notifyCellLocation(this);
    }

    @Override
    public void
    notifyCallForwardingIndicator() {
        mNotifier.notifyCallForwardingChanged(this);
    }

    /* M: call control part start */
    void notifySpeechCodecInfo(int type) {
        mSpeechCodecInfoRegistrants.notifyResult(type);
    }
    /* M: call control part end */

    // override for allowing access from other classes of this package
    /**
     * {@inheritDoc}
     */
    @Override
    public void
    setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(property, getSubId(), value);
    }

    @Override
    public void registerForSuppServiceNotification(
            Handler h, int what, Object obj) {
        mSsnRegistrants.addUnique(h, what, obj);
        if (mSsnRegistrants.size() == 1) mCi.setSuppServiceNotifications(true, null);
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mSsnRegistrants.remove(h);
        if (mSsnRegistrants.size() == 0) mCi.setSuppServiceNotifications(false, null);
    }

    @Override
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimRecordsLoaded(Handler h) {
        mSimRecordsLoadedRegistrants.remove(h);
    }

    @Override
    public void
    acceptCall(int videoState) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if ( imsPhone != null && imsPhone.getRingingCall().isRinging() ) {
            imsPhone.acceptCall(videoState);
        } else {
            mCT.acceptCall();
        }
    }

    @Override
    public void
    rejectCall() throws CallStateException {
        mCT.rejectCall();
    }

    @Override
    public void
    switchHoldingAndActive() throws CallStateException {
        mCT.switchWaitingOrHoldingAndActive();
    }

    @Override
    public boolean canConference() {
        boolean canImsConference = false;
        if (mImsPhone != null) {
            canImsConference = mImsPhone.canConference();
        }
        return mCT.canConference() || canImsConference;
    }

    public boolean canDial() {
        return mCT.canDial();
    }

    @Override
    public void conference() {
        if (mImsPhone != null && mImsPhone.canConference()) {
            log("conference() - delegated to IMS phone");
            mImsPhone.conference();
            return;
        }
        mCT.conference();
    }

    @Override
    public void clearDisconnected() {
        mCT.clearDisconnected();
    }

    @Override
    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    @Override
    public void explicitCallTransfer() {
        mCT.explicitCallTransfer();
    }

    @Override
    public GsmCall
    getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public GsmCall
    getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public Call getRingingCall() {
        ImsPhone imsPhone = mImsPhone;
        if ( mCT.mRingingCall != null && mCT.mRingingCall.isRinging() ) {
            return mCT.mRingingCall;
        } else if ( imsPhone != null ) {
            return imsPhone.getRingingCall();
        }
        return mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                mCT.rejectCall();
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 0: hangupWaitingOrBackground");
            mCT.hangupWaitingOrBackground();
        }

        return true;
    }

    private boolean handleCallWaitingIncallSupplementaryService(
            String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        try {
            if (len > 1) {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';

                if (callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: hangupConnectionByIndex " +
                            callIndex);
                    mCT.hangupConnectionByIndex(call, callIndex);
                }
            } else {
                if (call.getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: hangup foreground");
                    //mCT.hangupForegroundResumeBackground();
                    mCT.hangup(call);
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            }
        } catch (CallStateException e) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
        }

        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        if (len > 1) {
            try {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';
                GsmConnection conn = mCT.getConnectionByIndex(call, callIndex);

                // gsm index starts at 1, up to 5 connections in a call,
                if (conn != null && callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 2: separate call "+
                            callIndex);
                    mCT.separate(conn);
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "separate: invalid call index "+
                            callIndex);
                    notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "separate failed", e);
                notifySuppServiceFailed(Phone.SuppService.SEPARATE);
            }
        } else {
            try {
                if (getRingingCall().getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 2: accept ringing call");
                    mCT.acceptCall();
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 2: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "switch failed", e);
                notifySuppServiceFailed(Phone.SuppService.SWITCH);
            }
        }

        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {

        int len = dialString.length();

        if (len != 1) {
            return false;
        }

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        // Treat it as an "unknown" service.
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }

    @Override
    public boolean handleInCallMmiCommands(String dialString) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null
                && imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
            return imsPhone.handleInCallMmiCommands(dialString);
        }

        if (!isInCall()) {
            return false;
        }

        if (TextUtils.isEmpty(dialString)) {
            return false;
        }

        boolean result = false;
        char ch = dialString.charAt(0);
        switch (ch) {
            case '0':
                result = handleCallDeflectionIncallSupplementaryService(
                        dialString);
                break;
            case '1':
                result = handleCallWaitingIncallSupplementaryService(
                        dialString);
                break;
            case '2':
                result = handleCallHoldIncallSupplementaryService(dialString);
                break;
            case '3':
                result = handleMultipartyIncallSupplementaryService(dialString);
                break;
            case '4':
                result = handleEctIncallSupplementaryService(dialString);
                break;
            case '5':
                result = handleCcbsIncallSupplementaryService(dialString);
                break;
            default:
                break;
        }

        return result;
    }

    boolean isInCall() {
        GsmCall.State foregroundCallState = getForegroundCall().getState();
        GsmCall.State backgroundCallState = getBackgroundCall().getState();
        GsmCall.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
                backgroundCallState.isAlive() ||
                ringingCallState.isAlive());
    }

    @Override
    public Connection
    dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, null, videoState);
    }

    @Override
    public Connection
    dial (String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;

        boolean imsUseEnabled =
                ImsManager.isEnhanced4gLteModeSettingEnabledByPlatform(mContext) &&
                ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext);
        if (!imsUseEnabled) {
            Rlog.w(LOG_TAG, "IMS is disabled: forced to CS");
        }

        if (imsUseEnabled && imsPhone != null
                && ((imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE
                && !PhoneNumberUtils.isEmergencyNumber(dialString))
                || (PhoneNumberUtils.isEmergencyNumber(dialString)
                && mContext.getResources().getBoolean(
                        com.android.internal.R.bool.useImsAlwaysForEmergencyCall))) ) {
            try {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying IMS PS call");
                return imsPhone.dial(dialString, videoState);
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "IMS PS call exception " + e);
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    CallStateException ce = new CallStateException(e.getMessage());
                    ce.setStackTrace(e.getStackTrace());
                    throw ce;
                }
            }
        }

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        return dialInternal(dialString, null, VideoProfile.VideoState.AUDIO_ONLY);
    }

    @Override
    protected Connection
    dialInternal (String dialString, UUSInfo uusInfo, int videoState)
            throws CallStateException {

        /// M: Ignore stripping for VoLTE SIP uri. @{
        String newDialString = dialString;
        if (!PhoneNumberUtils.isUriNumber(dialString)) {
            // Need to make sure dialString gets parsed properly
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        /// @}

        // handle in-call MMI first if applicable
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }

        // Only look at the Network portion for mmi
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        /* M: SS part */
        Rlog.d(LOG_TAG, "network portion:" + networkPortion);
        /* M: SS part end */
        GsmMmiCode mmi =
                GsmMmiCode.newFromDialString(networkPortion, this, mUiccApplication.get());
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                               "dialing w/ mmi '" + mmi + "'...");

        if (mmi == null) {
            return mCT.dial(newDialString, uusInfo);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo);
        } else {
            /* M: SS part */
            Rlog.d(LOG_TAG, "[dial]mPendingMMIs.add(mmi) + " + mmi);
            /* M: SS part end */
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            // FIXME should this return null or something else?
            return null;
        }
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, mUiccApplication.get());

        if (mmi != null && mmi.isPinPukCommand()) {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return true;
        }

        return false;
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, mUiccApplication.get());
        /* M: SS part */
        Rlog.d(LOG_TAG, "[sendUssdResponse]mPendingMMIs.add(mmi) + " + mmi);
        /* M: SS part end */
        mPendingMMIs.add(mmi);
        mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    @Override
    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCi.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void
    startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                "startDtmf called with invalid character '" + c + "'");
        } else {
            mCi.startDtmf(c, null);
        }
    }

    @Override
    public void
    stopDtmf() {
        mCi.stopDtmf(null);
    }

    public void
    sendBurstDtmf(String dtmfString) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    @Override
    public void
    setRadioPower(boolean power) {
        mSST.setRadioPower(power);
    }

    private void storeVoiceMailNumber(String number) {
        Rlog.d(LOG_TAG, "[GSMPhone] storeVoiceMailNumber, to SP " + number);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_NUMBER + getPhoneId(), number);
        editor.apply();
        setVmSimImsi(getSubscriberId());
    }

    @Override
    public String getVoiceMailNumber() {
        // Read from the SIM. If its null, try reading from the shared preference area.
        IccRecords r = mIccRecords.get();
        String number = (r != null) ? r.getVoiceMailNumber() : "";
        Rlog.d(LOG_TAG, "[GSMPhone] getVoiceMailNumber, from SIMRecords " + number);
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            number = sp.getString(VM_NUMBER + getPhoneId(), null);
        }
        Rlog.d(LOG_TAG, "[GSMPhone] getVoiceMailNumber, from SP " + number);
        if (TextUtils.isEmpty(number)) {
            String[] listArray = getContext().getResources()
                .getStringArray(com.android.internal.R.array.config_default_vm_number);
            if (listArray != null && listArray.length > 0) {
                for (int i=0; i<listArray.length; i++) {
                    if (!TextUtils.isEmpty(listArray[i])) {
                        String[] defaultVMNumberArray = listArray[i].split(";");
                        if (defaultVMNumberArray != null && defaultVMNumberArray.length > 0) {
                            if (defaultVMNumberArray.length == 1) {
                                number = defaultVMNumberArray[0];
                            } else if (defaultVMNumberArray.length == 2 &&
                                    !TextUtils.isEmpty(defaultVMNumberArray[1]) &&
                                    defaultVMNumberArray[1].equalsIgnoreCase(getGroupIdLevel1())) {
                                number = defaultVMNumberArray[0];
                                break;
                            }
                        }
                    }
                }
            }
        }
        Rlog.d(LOG_TAG, "[GSMPhone] getVoiceMailNumber, final " + number);
        return number;
    }

    private String getVmSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(VM_SIM_IMSI + getPhoneId(), null);
    }

    private void setVmSimImsi(String imsi) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_SIM_IMSI + getPhoneId(), imsi);
        editor.apply();
    }

    @Override
    public String getVoiceMailAlphaTag() {
        String ret;
        IccRecords r = mIccRecords.get();

        ret = (r != null) ? r.getVoiceMailAlphaTag() : "";

        if (ret == null || ret.length() == 0) {
            return mContext.getText(
                com.android.internal.R.string.defaultVoiceMailAlphaTag).toString();
        }

        return ret;
    }

    @Override
    public String getDeviceId() {
        Rlog.d(LOG_TAG, "[GSMPhone] getDeviceId: " + mImei);
        return mImei;
    }

    @Override
    public String getDeviceSvn() {
        Rlog.d(LOG_TAG, "[GSMPhone] getDeviceSvn: " + mImeiSv);
        return mImeiSv;
    }

    @Override
    public IsimRecords getIsimRecords() {
        return mIsimUiccRecords;
    }

    @Override
    public String getImei() {
        return mImei;
    }

    @Override
    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    @Override
    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    @Override
    public String getSubscriberId() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getIMSI() : null;
    }

    @Override
    public String getGroupIdLevel1() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getGid1() : null;
    }

    @Override
    public String getLine1Number() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getMsisdn() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getLine1AlphaTag() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnAlphaTag() : null;
    }

    @Override
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
        }
    }

    @Override
    public void setVoiceMailNumber(String alphaTag,
                            String voiceMailNumber,
                            Message onComplete) {

        Rlog.d(LOG_TAG, "[GSMPhone] setVoiceMailNumber  alphaTag:" + alphaTag + " voiceMailNumber:" + voiceMailNumber);

        Message resp;
        mVmNumber = voiceMailNumber;
        resp = obtainMessage(EVENT_SET_VM_NUMBER_DONE, 0, 0, onComplete);
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, mVmNumber, resp);
        }
    }

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    public String getSystemProperty(String property, String defValue) {
        if(getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(property, getSubId(), defValue);
    }

    private boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    public void updateDataConnectionTracker() {
        ((DcTracker)mDcTracker).update();
    }

    protected  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
            return;
        }

        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }
            mCi.queryCallForwardStatus(commandInterfaceCFReason,0,null,resp);
        }
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.setCallForwardingOption(commandInterfaceCFAction,
                    commandInterfaceCFReason, dialingNumber, timerSeconds, onComplete);
            return;
        }

        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                Cfu cfu = new Cfu(dialingNumber, onComplete);
                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfu);
            } else {
                resp = onComplete;
            }
            mCi.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    CommandsInterface.SERVICE_CLASS_VOICE,
                    dialingNumber,
                    timerSeconds,
                    resp);
        }
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        mCi.getCLIR(onComplete);
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
                                           Message onComplete) {
        mCi.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.getCallWaiting(onComplete);
            return;
        }

        //As per 3GPP TS 24.083, section 1.6 UE doesn't need to send service
        //class parameter in call waiting interrogation  to network
        mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_NONE, onComplete);
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.setCallWaiting(enable, onComplete);
            return;
        }

        mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void getAvailableNetworks(Message response) {
        log("getAvailableNetworks");
        /** M: for suspend data during plmn list */
        mAvailableNetworkStep = PLMN_LIST_STEP_PS_DETACH;
        mAvailableNetworkMsg = response;
        // The detach command will timeout if no response over 5 mins
        DctController.getInstance().setDataAllowed(getSubId(), false, 330000);
        DctController.getInstance().registerForDcSwitchStateChange(
                this, EVENT_DC_SWITCH_STATE_CHANGE, response);
    }

    @Override
    public synchronized void cancelAvailableNetworks(Message response) {
        log("cancelAvailableNetworks: step = " + mAvailableNetworkStep);

        switch (mAvailableNetworkStep) {
            case PLMN_LIST_STEP_PS_DETACH:
                DctController.getInstance().unregisterForDcSwitchStateChange(this);
                AsyncResult.forMessage(mAvailableNetworkMsg, null,
                        new CommandException(Error.INVALID_RESPONSE));
                mAvailableNetworkMsg.sendToTarget();
                DctController.getInstance().setDataAllowed(getSubId(), true, 0);
                break;

            case PLMN_LIST_STEP_VIRTUAL_MODE:
                removeMessages(EVENT_GET_AVAILABLE_NETWORK);
                AsyncResult.forMessage(mAvailableNetworkMsg, null,
                        new CommandException(Error.INVALID_RESPONSE));
                mAvailableNetworkMsg.sendToTarget();
                DctController.getInstance().setDataAllowed(getSubId(), true, 0);
                break;

            case PLMN_LIST_STEP_SEARCH:
                mCi.unregisterForGetAvailableNetworksDone(this);
                DctController.getInstance().setDataAllowed(getSubId(), true, 0);
                break;

            default:
                break;

        }
        mAvailableNetworkStep = PLMN_LIST_STEP_DONE;
        mAvailableNetworkMsg = null;
        mCi.cancelAvailableNetworks(response);
    }

    @Override
    public void
    setNetworkSelectionModeSemiAutomatic(OperatorInfo network,Message response) {
        // wrap the response message in our own message along with
        // an empty string (to indicate automatic selection) for the
        // operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";

        Message msg = obtainMessage(EVENT_SET_NETWORK_AUTOMATIC_COMPLETE, nsm);

        String actype = ACT_TYPE_GSM;
        if(network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(UTRAN_INDICATOR)) {
            actype = ACT_TYPE_UTRAN;
        } else if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(LTE_INDICATOR)){
            actype = ACT_TYPE_LTE;
        }

        mCi.setNetworkSelectionModeSemiAutomatic(network.getOperatorNumeric(),actype, msg);
    }

    @Override
    public void
    selectNetworkManually(OperatorInfo network,
            Message response) {
        // wrap the response message in our own message along with
        // the operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();

        Message msg = obtainMessage(EVENT_SET_NETWORK_MANUAL_COMPLETE, nsm);

        Rlog.d(LOG_TAG, "GSMPhone selectNetworkManually() :" + network);

        String actype = ACT_TYPE_GSM;
        if(network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(UTRAN_INDICATOR)) {
            actype = ACT_TYPE_UTRAN;
        } else if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(LTE_INDICATOR)){
            actype = ACT_TYPE_LTE;
        }

        mCi.setNetworkSelectionModeManualWithAct(network.getOperatorNumeric(), actype, msg);
    }

    @Override
    public void
    getNeighboringCids(Message response) {
        mCi.getNeighboringCids(response);
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    @Override
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return mCT.getMute();
    }

    @Override
    public void getDataCallList(Message response) {
        mCi.getDataCallList(response);
    }

    @Override
    public void updateServiceLocation() {
        mSST.enableSingleLocationUpdate();
    }

    @Override
    public void enableLocationUpdates() {
        mSST.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mSST.disableLocationUpdates();
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mDcTracker.getDataOnRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mDcTracker.setDataOnRoamingEnabled(enable);
    }

    @Override
    public boolean getDataEnabled() {
        return mDcTracker.getDataEnabled();
    }

    @Override
    public void setDataEnabled(boolean enable) {
        mDcTracker.setDataEnabled(enable);
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     */
    /*package*/ void
    onMMIDone(GsmMmiCode mmi) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */
        /* M: SS part */
        Rlog.d(LOG_TAG, "mPendingMMIs.remove(mmi) - " + mmi);
        /* M: SS part end */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                new AsyncResult(null, mmi, null));
        }
    }


    private void
    onNetworkInitiatedUssd(GsmMmiCode mmi) {
        mMmiCompleteRegistrants.notifyRegistrants(
            new AsyncResult(null, mmi, null));
    }


    /** ussdMode is one of CommandsInterface.USSD_MODE_* */
    private void
    onIncomingUSSD (int ussdMode, String ussdMessage) {
        boolean isUssdError;
        boolean isUssdRequest;

        isUssdRequest
            = (ussdMode == CommandsInterface.USSD_MODE_REQUEST);
        /* M: SS part */
        //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
        isUssdError
            = ((ussdMode == CommandsInterface.USSD_OPERATION_NOT_SUPPORTED)
               || (ussdMode == CommandsInterface.USSD_NETWORK_TIMEOUT));
        //MTK-END [mtk04070][111118][ALPS00093395]MTK modified
        /* M: SS part end */

        // See comments in GsmMmiCode.java
        // USSD requests aren't finished until one
        // of these two events happen
        GsmMmiCode found = null;
        for (int i = 0, s = mPendingMMIs.size() ; i < s; i++) {
            if(mPendingMMIs.get(i).isPendingUSSD()) {
                found = mPendingMMIs.get(i);
                break;
            }
        }

        if (found != null) {
            // Complete pending USSD
            /* M: SS part */
            //For ALPS01471897
            Rlog.d(LOG_TAG, "setUserInitiatedMMI  TRUE");
            found.setUserInitiatedMMI(true);
            /* M: SS part end */
            if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else { // pending USSD not found
            // The network may initiate its own USSD request

            // ignore everything that isnt a Notify or a Request
            // also, discard if there is no message to present

            /* M: SS part */
            //For ALPS01471897
            Rlog.d(LOG_TAG, "The default value of UserInitiatedMMI is FALSE");

            if (!isUssdError && ussdMessage != null) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssd(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);

            //MTK-START [mtk04070][111118][ALPS00093395]MTK added
            } else if (isUssdError) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssdError(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);
            //MTK-END [mtk04070][111118][ALPS00093395]MTK added
            }
            /* M: SS part end */
        }
    }

    /**
     * Make sure the network knows our preferred setting.
     */
    protected  void syncClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        /* M: SS part *///TODO  check sp.getInt(xxx+getPhoneID())
        /// M: Add key for SIM2 CLIR setting.
        String keyName = (getPhoneId() == PhoneConstants.SUB1) ? CLIR_KEY : CLIR_KEY_2;

        //int clirSetting = sp.getInt(CLIR_KEY, -1);
        int clirSetting = sp.getInt(CLIR_KEY + getPhoneId(), -1);
        /* M: SS part end */
        if (clirSetting >= 0) {
            mCi.setCLIR(clirSetting, null);
        }
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        Message onComplete;

        if (!mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case EVENT_RADIO_AVAILABLE: {
                mCi.getBasebandVersion(
                        obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));

                mCi.getIMEI(obtainMessage(EVENT_GET_IMEI_DONE));
                mCi.getIMEISV(obtainMessage(EVENT_GET_IMEISV_DONE));
                mCi.getPhoneRatFamily(obtainMessage(EVENT_GET_PHONE_RAT_FAMILY));
            }
            break;

            case EVENT_RADIO_ON:
                // do-nothing
                break;

            case EVENT_REGISTERED_TO_NETWORK:
                syncClirSetting();
                /* M: SS part */
                if (needQueryCfu) {
                    String cfuSetting = SystemProperties.get(PhoneConstants.CFU_QUERY_TYPE_PROP, PhoneConstants.CFU_QUERY_TYPE_DEF_VALUE);
                    String isTestSim = "0";
                    /// M: Add for CMCC RRM test. @{
                    boolean isRRMEnv = false;
                    String operatorNumeric = null;
                    /// @}
                    if (getSubId() == PhoneConstants.SUB1) {
                        isTestSim = SystemProperties.get("gsm.sim.ril.testsim", "0");
                    }
                    else if (getSubId() == PhoneConstants.SUB2) {
                        isTestSim = SystemProperties.get("gsm.sim.ril.testsim.2", "0");
                    }

                    /// M: Add for CMCC RRM test. @{
                    // RRM test use 46602 as PLMN, which will not appear in the actual network
                    // Note that this should be modified when the PLMN for RRM test is changed
                    operatorNumeric = getServiceState().getOperatorNumeric();
                    if (operatorNumeric != null && operatorNumeric.equals("46602")) {
                        isRRMEnv = true;
                    }
                    /// @}
                    Rlog.d(LOG_TAG, "[GSMPhone] CFU_KEY = " + cfuSetting + " isTestSIM : " + isTestSim + " isRRMEnv : " + isRRMEnv);

                    if (isTestSim.equals("0") && isRRMEnv == false) { /// M: Add for CMCC RRM test.
                        String isChangedProp = CFU_QUERY_SIM_CHANGED_PROP + getPhoneId();
                        String isChanged = SystemProperties.get(isChangedProp, "0");

                        Rlog.d(LOG_TAG, "[GSMPhone] isChanged " + isChanged);
                        // 0 : default
                        // 1 : OFF
                        // 2 : ON
                        if (cfuSetting.equals("2")
                            || (cfuSetting.equals("0") && isChanged.equals("1"))) {
                            /* For solving ALPS01023811 */
                            mCfuQueryRetryCount = 0;
                            queryCfuOrWait();
                            needQueryCfu = false;
                            SystemProperties.set(isChangedProp, "0");
                        }
                    }
                /* M: SS part end */
                }
                break;

            case EVENT_SIM_RECORDS_LOADED:
                updateCurrentCarrierInProvider();

                // Check if this is a different SIM than the previous one. If so unset the
                // voice mail number.
                String imsi = getVmSimImsi();
                String imsiFromSIM = getSubscriberId();
                if (imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) {
                    storeVoiceMailNumber(null);
                    setVmSimImsi(null);
                }

                mSimRecordsLoadedRegistrants.notifyRegistrants();
            break;

            case EVENT_GET_BASEBAND_VERSION_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                /// M: c2k modify, support BASEBAND version of stack 2. @{
                if (LOCAL_DEBUG) {
                    Rlog.d(LOG_TAG, "mPhoneId: " + mPhoneId + ", Baseband version: " + ar.result);
                }
                if (PhoneFactory.isEvdoDTSupport() && mPhoneId == PhoneConstants.SIM_ID_2) {
                    setSystemProperty(PROPERTY_BASEBAND_VERSION_2, (String) ar.result);
                } else {
                    setSystemProperty(PROPERTY_BASEBAND_VERSION, (String) ar.result);
                }
                /// @}
            break;

            case EVENT_GET_IMEI_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "Null IMEI!!");
                    setDeviceIdAbnormal(1);
                    break;
                }

                mImei = (String)ar.result;
                Rlog.d(LOG_TAG, "IMEI: " + mImei);

                try {
                    Long.parseLong(mImei);
                    setDeviceIdAbnormal(0);
                } catch (NumberFormatException e) {
                    setDeviceIdAbnormal(1);
                    Rlog.d(LOG_TAG, "Invalid format IMEI!!");
                }
            break;

            case EVENT_GET_IMEISV_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                mImeiSv = (String)ar.result;
            break;

            case EVENT_USSD:
                ar = (AsyncResult)msg.obj;

                String[] ussdResult = (String[]) ar.result;

                if (ussdResult.length > 1) {
                    try {
                        onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                    } catch (NumberFormatException e) {
                        Rlog.w(LOG_TAG, "error parsing USSD");
                    }
                }
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE: {
                // Some MMI requests (eg USSD) are not completed
                // within the course of a CommandsInterface request
                // If the radio shuts off or resets while one of these
                // is pending, we need to clean up.

                for (int i = mPendingMMIs.size() - 1; i >= 0; i--) {
                    if (mPendingMMIs.get(i).isPendingUSSD()) {
                        mPendingMMIs.get(i).onUssdFinishedError();
                    }
                }
                ImsPhone imsPhone = mImsPhone;
                if (imsPhone != null) {
                    imsPhone.getServiceState().setStateOff();
                }
                /* M: call control part start */
                mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                /* M: call control part end */
            }
            break;

            case EVENT_SSN:
                ar = (AsyncResult)msg.obj;
                SuppServiceNotification not = (SuppServiceNotification) ar.result;
                mSsnRegistrants.notifyRegistrants(ar);
            break;

            case EVENT_SET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                IccRecords r = mIccRecords.get();
                Cfu cfu = (Cfu) ar.userObj;
                if (ar.exception == null && r != null) {
                    r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                }
                if (cfu.mOnComplete != null) {
                    AsyncResult.forMessage(cfu.mOnComplete, ar.result, ar.exception);
                    cfu.mOnComplete.sendToTarget();
                }
                break;

            case EVENT_SET_VM_NUMBER_DONE:
                ar = (AsyncResult)msg.obj;
                Rlog.d(LOG_TAG, "[GSMPhone] handle EVENT_SET_VM_NUMBER_DONE");
                if (IccVmNotSupportedException.class.isInstance(ar.exception)) {
                    storeVoiceMailNumber(mVmNumber);
                    ar.exception = null;
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;


            case EVENT_GET_CALL_FORWARD_DONE:
                /* M: SS part */ //TODO need check mPhoneID
                /* For solving ALPS00997715 */
                Rlog.d(LOG_TAG, "mPhoneId= " + mPhoneId + "subId=" + getSubId());
                setSystemProperty(CFU_QUERY_PROPERTY_NAME + mPhoneId, "0");
                ar = (AsyncResult)msg.obj;
                Rlog.d(LOG_TAG, "[EVENT_GET_CALL_FORWARD_DONE]ar.exception = " + ar.exception);
                /* M: SS part end */
                if (ar.exception == null) {
                    handleCfuQueryResult((CallForwardInfo[])ar.result);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SET_NETWORK_AUTOMATIC:
                // Automatic network selection from EF_CSP SIM record
                ar = (AsyncResult) msg.obj;
                if (mSST.mSS.getIsManualSelection()) {
                    setNetworkSelectionModeAutomatic((Message) ar.result);
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                } else {
                    // prevent duplicate request which will push current PLMN to low priority
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                }
                break;

            case EVENT_ICC_RECORD_EVENTS:
                ar = (AsyncResult)msg.obj;
                processIccRecordEvents((Integer)ar.result);
                break;
            /* M: SS part */
            case EVENT_SET_CLIR_COMPLETE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    saveClirSetting(msg.arg1);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_GET_PHONE_RAT_FAMILY:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    if (LOCAL_DEBUG) {
                        Rlog.d(LOG_TAG, "get phone RAT fail, no need to change mPhoneRatFamily");
                    }
                } else {
                    mPhoneRatFamily = ((int[]) ar.result)[0];
                }
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                        "EVENT_GET_PHONE_RAT_FAMILY : phone RAT family : " + mPhoneRatFamily);
                break;

             //MTK-START [mtk04070][111118][ALPS00093395]MTK added
             case EVENT_CFU_IND:
                /* Line1 is enabled or disabled while reveiving this EVENT */
                if (mIccRecords.get() != null) {
                   /* Line1 is enabled or disabled while reveiving this EVENT */
                   ar = (AsyncResult) msg.obj;
                   int[] cfuResult = (int[]) ar.result;
                   mIccRecords.get().setVoiceCallForwardingFlag(1, (cfuResult[0] == 1), null);
                }
                break;
             case EVENT_CRSS_IND:
                ar = (AsyncResult) msg.obj;
                SuppCrssNotification noti = (SuppCrssNotification) ar.result;

                if (noti.code == SuppCrssNotification.CRSS_CALLING_LINE_ID_PREST) {
                    // update numberPresentation in gsmconnection
                    if (getRingingCall().getState() != GsmCall.State.IDLE) {
                        GsmConnection cn = (GsmConnection) (getRingingCall().getConnections().get(0));
                        /* CLI validity value,
                          0: PRESENTATION_ALLOWED,
                          1: PRESENTATION_RESTRICTED,
                          2: PRESENTATION_UNKNOWN
                        */

                        Rlog.d(LOG_TAG, "set number presentation to connection : " + noti.cli_validity);
                        switch (noti.cli_validity) {
                            case 1:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_RESTRICTED);
                                break;

                            case 2:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_UNKNOWN);
                                break;

                            case 0:
                            default:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_ALLOWED);
                                break;
                        }
                    }
                } else if (noti.code == SuppCrssNotification.CRSS_CONNECTED_LINE_ID_PREST) {
                    /* If the phone number contains in +COLP is different from the address of connection,
                       store it to connection as redirecting address.
                    */
                    Rlog.d(LOG_TAG, "[COLP]noti.number = " + noti.number);
                    if (getForegroundCall().getState() != GsmCall.State.IDLE) {
                        Connection cn = (Connection) (getForegroundCall().getConnections().get(0));
                        if ((cn != null) &&
                            (cn.getAddress() != null) &&
                            !cn.getAddress().equals(noti.number)) {
                           cn.setRedirectingAddress(noti.number);
                        }
                        Rlog.d(LOG_TAG, "[COLP]Redirecting address = " + cn.getRedirectingAddress());
                    }
                }

                mCallRelatedSuppSvcRegistrants.notifyRegistrants(ar);

                break;

            case EVENT_CFU_QUERY_TIMEOUT:
                Rlog.d(LOG_TAG, "[EVENT_CFU_QUERY_TIMEOUT]mCfuQueryRetryCount = " + mCfuQueryRetryCount);
                if (++mCfuQueryRetryCount < CFU_QUERY_MAX_COUNT) {
                   queryCfuOrWait();
                }
                break;
            /* M: SS part end */

            case EVENT_SUBSCRIPTION_ACTIVATED:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                break;

            case EVENT_SUBSCRIPTION_DEACTIVATED:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                break;

            /* M: call control part start */
            case EVENT_VOICE_CALL_INCOMING_INDICATION:
                log("handle EVENT_VOICE_CALL_INCOMING_INDICATION");
                mVoiceCallIncomingIndicationRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
                break;
            /* M: call control part end */

            case EVENT_GET_AVAILABLE_NETWORK_DONE:
                if (mAvailableNetworkStep == PLMN_LIST_STEP_SEARCH) {
                    log("Available Network Step 4: Get available network done");
                    mAvailableNetworkStep = PLMN_LIST_STEP_DONE;
                    /** M: for suspend data during plmn list */
                    mCi.unregisterForGetAvailableNetworksDone(this);
                    DctController.getInstance().setDataAllowed(getSubId(), true, 0);
                }
                break;

            case EVENT_GET_AVAILABLE_NETWORK:
                if (mAvailableNetworkStep == PLMN_LIST_STEP_VIRTUAL_MODE) {
                    log("Available Network Step 3:Query available network");
                    mAvailableNetworkStep = PLMN_LIST_STEP_SEARCH;
                    mCi.registerForGetAvailableNetworksDone(this,
                            EVENT_GET_AVAILABLE_NETWORK_DONE, null);
                    mCi.getAvailableNetworks((Message) msg.obj);
                    DctController.getInstance().unregisterForDcSwitchStateChange(this);
                }
                break;

            case EVENT_DC_SWITCH_STATE_CHANGE:
                if (mAvailableNetworkStep == PLMN_LIST_STEP_PS_DETACH) {
                    ar = (AsyncResult) msg.obj;
                    String state = (String) ar.result;

                    log("handle EVENT_DC_SWITCH_STATE_CHANGE, state = " + state);

                    if (state.equals(DcSwitchState.DCSTATE_IDLE)) {
                        log("Available Network Step 2: Wait modem leave virtual mode");
                        mAvailableNetworkStep = PLMN_LIST_STEP_VIRTUAL_MODE;
                        Message message = obtainMessage(EVENT_GET_AVAILABLE_NETWORK);
                        message.obj = (Object) ((Message) ar.userObj);
                        //ALPS01655137: We need to wait 1.5 second for leaving vitrual mode.
                        sendMessageDelayed(message, 1500);
                    }
                }
                break;
            default:
                super.handleMessage(msg);
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
            return  ((UiccController) mUiccController).getUiccCardApplication(mPhoneId,
                    UiccController.APP_FAM_3GPP);
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(mPhoneId, UiccController.APP_FAM_IMS);
        IsimUiccRecords newIsimUiccRecords = null;

        if (newUiccApplication != null) {
            newIsimUiccRecords = (IsimUiccRecords)newUiccApplication.getIccRecords();
            if (LOCAL_DEBUG) log("New ISIM application found");
        }
        mIsimUiccRecords = newIsimUiccRecords;

        newUiccApplication = getUiccCardApplication();

        UiccCardApplication app = mUiccApplication.get();
        if (app != newUiccApplication) {
            if (app != null) {
                if (LOCAL_DEBUG) log("Removing stale icc objects.");
                if (mIccRecords.get() != null) {
                    unregisterForSimRecordEvents();
                    mSimPhoneBookIntManager.updateIccRecords(null);
                }
                mIccRecords.set(null);
                mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                if (LOCAL_DEBUG) log("New Uicc application found");
                mUiccApplication.set(newUiccApplication);
                mIccRecords.set(newUiccApplication.getIccRecords());
                registerForSimRecordEvents();
                mSimPhoneBookIntManager.updateIccRecords(mIccRecords.get());
            }

            /* M: SS part */
            /* To solve [ALPS00455020]No CFU icon showed on status bar. */
            if (!mIsCfuRegistered) {
                mIsCfuRegistered = true;
                /* register for CFU info flag notification */
                //mCi.registerForCallForwardingInfo(this, EVENT_CFU_IND, null);
            }
            /* M: SS part end */
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case IccRecords.EVENT_CFI:
                notifyCallForwardingIndicator();
                break;
            case IccRecords.EVENT_MWI:
                notifyMessageWaitingIndicator();
                break;
        }
    }

   /**
     * Sets the "current" field in the telephony provider according to the SIM's operator
     *
     * @return true for success; false otherwise.
     */
    public boolean updateCurrentCarrierInProvider() {
        long currentDds = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();

        log("updateCurrentCarrierInProvider: mSubId = " + getSubId()
                + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);

        if (!TextUtils.isEmpty(operatorNumeric) && (getSubId() == currentDds)) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "current");
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    /**
     * Saves CLIR setting so that we can re-apply it as necessary
     * (in case the RIL resets it across reboots).
     */
    public void saveClirSetting(int commandInterfaceCLIRMode) {
        // open the shared preferences editor, and write the value.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        /* M: SS part */ //TODO need to review the CLIR_KEY
        /// M: Add key for SIM2 CLIR setting.
        //String keyName = (getMySimId()==PhoneConstants.GEMINI_SIM_1) ? CLIR_KEY : CLIR_KEY_2;
        SharedPreferences.Editor editor = sp.edit();

        //editor.putInt(keyName, commandInterfaceCLIRMode);
        editor.putInt(CLIR_KEY + getPhoneId(), commandInterfaceCLIRMode);
        // /* M: SS part end */

        // commit and log the result.
        if (! editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            if (infos == null || infos.length == 0) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
            } else {
                for (int i = 0, s = infos.length; i < s; i++) {
                    if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].status == 1),
                            infos[i].number);
                        // should only have the one
                        break;
                    }
                }
            }
        }
    }

    /**
     * Retrieves the PhoneSubInfo of the GSMPhone
     */
    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return mSubInfo;
    }

    /**
     * Retrieves the IccPhoneBookInterfaceManager of the GSMPhone
     */
    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return mSimPhoneBookIntManager;
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate 0 = activate, 1 = deactivate
     * @param response Callback message is empty on completion
     */
    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override
    public boolean isCspPlmnEnabled() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.isCspPlmnEnabled() : false;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.registerForNetworkSelectionModeAutomatic(
                this, EVENT_SET_NETWORK_AUTOMATIC, null);
        r.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        r.registerForRecordsLoaded(this, EVENT_SIM_RECORDS_LOADED, null);
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.unregisterForNetworkSelectionModeAutomatic(this);
        r.unregisterForRecordsEvents(this);
        r.unregisterForRecordsLoaded(this);
    }

    public void forceNotifyServiceStateChange() {
        super.notifyServiceStateChangedP(mSST.mSS);
    }

    @Override
    public void exitEmergencyCallbackMode() {
        if (mImsPhone != null) {
            mImsPhone.exitEmergencyCallbackMode();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + mCT);
        pw.println(" mSST=" + mSST);
        pw.println(" mPendingMMIs=" + mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + mSubInfo);
        if (VDBG) pw.println(" mImei=" + mImei);
        if (VDBG) pw.println(" mImeiSv=" + mImeiSv);
        pw.println(" mVmNumber=" + mVmNumber);
    }

    @Override
    public boolean setOperatorBrandOverride(String brand) {
        if (mUiccController == null) {
            return false;
        }

        UiccCard card = mUiccController.getUiccCard();
        if (card == null) {
            return false;
        }

        boolean status = card.setOperatorBrandOverride(brand);

        // Refresh.
        if (status) {
            IccRecords iccRecords = mIccRecords.get();
            if (iccRecords != null) {
                SystemProperties.set(TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA,
                        iccRecords.getServiceProviderName());
            }
            if (mSST != null) {
                mSST.pollState();
            }
        }
        return status;
    }

    /**
     * @return operator numeric.
     */
    public String getOperatorNumeric() {
        String operatorNumeric = null;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            operatorNumeric = r.getOperatorNumeric();
        }
        return operatorNumeric;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker)mDcTracker)
                .registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker)mDcTracker).unregisterForAllDataDisconnected(h);
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker)mDcTracker)
                .setInternalDataEnabled(enable, onCompleteMsg);
    }


    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker)mDcTracker)
                .setInternalDataEnabledFlag(enable);
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        mEcmTimerResetRegistrants.notifyResult(flag);
    }

    /**
     * Registration point for Ecm timer reset
     *
     * @param h handler to notify
     * @param what User-defined message code
     * @param obj placed in Message.obj
     */
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        mEcmTimerResetRegistrants.remove(h);
    }

    public void resetSubSpecifics() {
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GSMPhone] " + s);
    }

    /* M: call control part start */
    public void
    hangupAll() throws CallStateException {
        mCT.hangupAll();
    }

    /**
     * Set EAIC to accept or reject modem to send MT call related notifications.
     *
     * @param accept {@code true} if accept; {@code false} if reject.
     * @internal
     */
    public void setIncomingCallIndicationResponse(boolean accept) {
        log("setIncomingCallIndicationResponse " + accept);
        mCT.setIncomingCallIndicationResponse(accept);
    }

    public void registerForCrssSuppServiceNotification(
            Handler h, int what, Object obj) {
        mCallRelatedSuppSvcRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCrssSuppServiceNotification(Handler h) {
        mCallRelatedSuppSvcRegistrants.remove(h);
    }
    /* M: call control part end */

    /* M: SS part */
    /**
     * Get Call Barring State
     */
    public void getFacilityLock(String facility, String password, Message onComplete) {

        mCi.queryFacilityLock(facility, password, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    /**
     * Set Call Barring State
     */

    public void setFacilityLock(String facility, boolean enable, String password, Message onComplete) {

        mCi.setFacilityLock(facility, enable, password, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);

    }

    /**
     * Change Call Barring Password
     */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {

        mCi.changeBarringPassword(facility, oldPwd, newPwd, onComplete);

    }

    /**
     * Change Call Barring Password with confirm
     */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm, Message onComplete) {

        mCi.changeBarringPassword(facility, oldPwd, newPwd, newCfm, onComplete);

    }
    /* M: SS part end */

    public boolean queryCfuOrWait() {
        int sid1 = 99, sid2 = 99;
        /* M: SS part */ //TODO need to check if there any new implementation
        //int slotId = SubscriptionManager.getSlotId(getSubId());//reference code

        /*
        if (mySimId == PhoneConstants.GEMINI_SIM_1) {
           sid1 = PhoneConstants.GEMINI_SIM_2;
           sid2 = PhoneConstants.GEMINI_SIM_3;
        } else if (mySimId == PhoneConstants.GEMINI_SIM_2) {
           sid1 = PhoneConstants.GEMINI_SIM_1;
           sid2 = PhoneConstants.GEMINI_SIM_3;
        } else if (mySimId == PhoneConstants.GEMINI_SIM_3) {
           sid1 = PhoneConstants.GEMINI_SIM_1;
           sid2 = PhoneConstants.GEMINI_SIM_2;
        }*/
        String oppositePropertyValue1 = SystemProperties.get(CFU_QUERY_PROPERTY_NAME + sid1);
        String oppositePropertyValue2 = SystemProperties.get(CFU_QUERY_PROPERTY_NAME + sid2);
        if ((oppositePropertyValue1.equals("1")) ||
            (oppositePropertyValue2.equals("1"))) { /* The opposite phone is querying CFU status */
           Message message = obtainMessage(EVENT_CFU_QUERY_TIMEOUT);
           sendMessageDelayed(message, cfuQueryWaitTime);
           return false;
        } else {
           //setSystemProperty(CFU_QUERY_PROPERTY_NAME + mySimId, "1");//* M: SS part */TODO
           mCi.queryCallForwardStatus(CF_REASON_UNCONDITIONAL, SERVICE_CLASS_VOICE, null, obtainMessage(EVENT_GET_CALL_FORWARD_DONE));
           return true;
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (LOCAL_DEBUG) Rlog.w(LOG_TAG, "received broadcast " + action);

            /* M: SS part */
            if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                SubInfoRecord mySubInfo =
                    SubscriptionManager.getSubInfoForSubscriber(getSubId());

                String mySettingName = CFU_QUERY_ICCID_PROP + getPhoneId();
                String oldIccId = SystemProperties.get(mySettingName, "");

                if (mySubInfo != null) {
                    if (!mySubInfo.iccId.equals(oldIccId)) {
                        Rlog.w(LOG_TAG, " mySubId " + getSubId() + " mySettingName " + mySettingName
                                + " old iccid : " + oldIccId + " new iccid : " + mySubInfo.iccId);
                        SystemProperties.set(mySettingName, mySubInfo.iccId);
                        String isChanged = CFU_QUERY_SIM_CHANGED_PROP + getPhoneId();
                        SystemProperties.set(isChanged, "1");
                        needQueryCfu = true;
                    }
                }
            }
            /* M: SS part end */
        }/* end of onReceive */
    };


    public int getPhoneId() {
        return mPhoneId;
    }
    /* M: SS part end */

    // Added by M begin

    // ALPS00302702 RAT balancing
    public int getEfRatBalancing() {
        if (mIccRecords.get() != null) {
            return mIccRecords.get().getEfRatBalancing();
        }
        return 0;
    }

    // MVNO-API START
    public String getMvnoMatchType() {
        String type = PhoneConstants.MVNO_TYPE_NONE;
        if (mIccRecords.get() != null) {
            type = mIccRecords.get().getMvnoMatchType();
        }
        log("getMvnoMatchType: Type = " + type);
        return type;
    }

    public String getMvnoPattern(String type) {
        String pattern = "";
        log("getMvnoPattern:Type = " + type);

        if (mIccRecords.get() != null) {
            if (type.equals(PhoneConstants.MVNO_TYPE_SPN)) {
                pattern = mIccRecords.get().getSpNameInEfSpn();
            } else if (type.equals(PhoneConstants.MVNO_TYPE_IMSI)) {
                pattern = mIccRecords.get().isOperatorMvnoForImsi();
            } else if (type.equals(PhoneConstants.MVNO_TYPE_PNN)) {
                pattern = mIccRecords.get().isOperatorMvnoForEfPnn();
            } else if (type.equals(PhoneConstants.MVNO_TYPE_GID)) {
                pattern = mIccRecords.get().getGid1();
            } else {
                log("getMvnoPattern: Wrong type.");
            }
        }
        log("getMvnoPattern: pattern = " + pattern);
        return pattern;
    }

    // MVNO-API END
    public void setTrm(int mode, Message response) {
        mCi.setTrm(mode, response);
    }

    /**
     * Request security context authentication for USIM/SIM/ISIM
     */
    public void doGeneralSimAuthentication(int sessionId, int mode, int tag,
            String param1, String param2, Message result) {
        mCi.doGeneralSimAuthentication(sessionId, mode, tag, param1, param2, result);
    }

    public void queryPhbStorageInfo(int type, Message response) {
        mCi.queryPhbStorageInfo(type, response);
    }
    // Added by M end


    public void getPolCapability(Message onComplete) {
        mCi.getPOLCapabilty(onComplete);
    }

    public void getPol(Message onComplete) {
        mCi.getCurrentPOLList(onComplete);
    }

    public void setPolEntry(NetworkInfoWithAcT networkWithAct, Message onComplete) {
        mCi.setPOLEntry(networkWithAct.getPriority(), networkWithAct.getOperatorNumeric(),
                                    networkWithAct.getAccessTechnology(), onComplete);
    }

    /**
       * Check if phone is hiding network temporary out of service state
       * @return if phone is hiding network temporary out of service state.
       */
    public int getNetworkHideState() {
        if (mSST.dontUpdateNetworkStateFlag == true) {
            return ServiceState.STATE_OUT_OF_SERVICE;
        } else {
            return mSST.mSS.getState();
        }
    }

    /**
     * Returns current located PLMN string(ex: "46000") or null if not availble (ex: in flight mode or no signal area)
     */
    public String getLocatedPlmn() {
        return mSST.getLocatedPlmn();
    }

    /**
     * Refresh Spn Display due to configuration change
     @internal
     */
    public void refreshSpnDisplay() {
        mSST.refreshSpnDisplay();
    }

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric, int rat, Message response) {
        Rlog.d(LOG_TAG, "getFemtoCellList(),operatorNumeric=" + operatorNumeric + ",rat=" + rat);
        mCi.getFemtoCellList(operatorNumeric, rat, response);
    }

    public void abortFemtoCellList(Message response) {
        Rlog.d(LOG_TAG, "abortFemtoCellList()");
        mCi.abortFemtoCellList(response);
    }

    public void selectFemtoCell(FemtoCellInfo femtocell, Message response) {
        Rlog.d(LOG_TAG, "selectFemtoCell(): " + femtocell);
        mCi.selectFemtoCell(femtocell, response);
    }
    // Femtocell (CSG) feature END

    public boolean getImsRegInfo() {
        return mSST.getImsRegInfo();
    }

    public String getImsExtInfo() {
        return mSST.getImsExtInfo();
    }

    // VOLTE
    public void clearDataBearer() {
        mDcTracker.clearDataBearer();
    }

    public int isDeviceIdAbnormal() {
        return mImeiAbnormal;
    }

    public void setDeviceIdAbnormal(int abnormal) {
        mImeiAbnormal = abnormal;
    }
}
