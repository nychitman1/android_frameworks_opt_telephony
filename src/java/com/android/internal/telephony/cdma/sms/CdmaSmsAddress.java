/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.telephony.cdma.sms;

import android.util.SparseBooleanArray;
import android.telephony.Rlog;

import com.android.internal.telephony.SmsAddress;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.HexDump;

public class CdmaSmsAddress extends SmsAddress {
    private final static String TAG = "CdmaSmsAddress";

    /**
     * Digit Mode Indicator is a 1-bit value that indicates whether
     * the address digits are 4-bit DTMF codes or 8-bit codes.  (See
     * 3GPP2 C.S0015-B, v2, 3.4.3.3)
     */
    static public final int DIGIT_MODE_4BIT_DTMF              = 0x00;
    static public final int DIGIT_MODE_8BIT_CHAR              = 0x01;

    public int digitMode;

    /**
     * Number Mode Indicator is 1-bit value that indicates whether the
     * address type is a data network address or not.  (See 3GPP2
     * C.S0015-B, v2, 3.4.3.3)
     */
    static public final int NUMBER_MODE_NOT_DATA_NETWORK      = 0x00;
    static public final int NUMBER_MODE_DATA_NETWORK          = 0x01;

    public int numberMode;

    /**
     * Number Types for data networks.
     * (See 3GPP2 C.S005-D, table2.7.1.3.2.4-2 for complete table)
     * (See 3GPP2 C.S0015-B, v2, 3.4.3.3 for data network subset)
     * NOTE: value is stored in the parent class ton field.
     */
    static public final int TON_UNKNOWN                   = 0x00;
    static public final int TON_INTERNATIONAL_OR_IP       = 0x01;
    static public final int TON_NATIONAL_OR_EMAIL         = 0x02;
    static public final int TON_NETWORK                   = 0x03;
    static public final int TON_SUBSCRIBER                = 0x04;
    static public final int TON_ALPHANUMERIC              = 0x05;
    static public final int TON_ABBREVIATED               = 0x06;
    static public final int TON_RESERVED                  = 0x07;

    /**
     * Maximum lengths for fields as defined in ril_cdma_sms.h.
     */
    static public final int SMS_ADDRESS_MAX          =  36;
    static public final int SMS_SUBADDRESS_MAX       =  36;

    /**
     * This field shall be set to the number of address digits
     * (See 3GPP2 C.S0015-B, v2, 3.4.3.3)
     */
    public int numberOfDigits;

    /**
     * Numbering Plan identification is a 0 or 4-bit value that
     * indicates which numbering plan identification is set.  (See
     * 3GPP2, C.S0015-B, v2, 3.4.3.3 and C.S005-D, table2.7.1.3.2.4-3)
     */
    static public final int NUMBERING_PLAN_UNKNOWN           = 0x0;
    static public final int NUMBERING_PLAN_ISDN_TELEPHONY    = 0x1;
    //static protected final int NUMBERING_PLAN_DATA              = 0x3;
    //static protected final int NUMBERING_PLAN_TELEX             = 0x4;
    //static protected final int NUMBERING_PLAN_PRIVATE           = 0x9;

    public int numberPlan;

    /**
     * NOTE: the parsed string address and the raw byte array values
     * are stored in the parent class address and origBytes fields,
     * respectively.
     */

    public CdmaSmsAddress(){
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CdmaSmsAddress ");
        builder.append("{ digitMode=" + digitMode);
        builder.append(", numberMode=" + numberMode);
        builder.append(", numberPlan=" + numberPlan);
        builder.append(", numberOfDigits=" + numberOfDigits);
        builder.append(", ton=" + ton);
        builder.append(", address=\"" + address + "\"");
        if (origBytes != null) {
            builder.append(", origBytes=" + HexDump.toHexString(origBytes));
        } else {
            builder.append(", origBytes=null");
        }
        builder.append(" }");
        return builder.toString();
    }

    /*
     * TODO(cleanup): Refactor the parsing for addresses to better
     * share code and logic with GSM.  Also, gather all DTMF/BCD
     * processing code in one place.
     */

    private static byte[] parseToDtmf(String address) {
        int digits = address.length();
        byte[] result = new byte[digits];
        for (int i = 0; i < digits; i++) {
            char c = address.charAt(i);
            int val = 0;
            if ((c >= '1') && (c <= '9')) val = c - '0';
            else if (c == '0') val = 10;
            else if (c == '*') val = 11;
            else if (c == '#') val = 12;
            else return null;
            result[i] = (byte)val;
        }
        return result;
    }

    private static final char[] numericCharsDialable = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#'
    };

    private static final char[] numericCharsSugar = {
        '(', ')', ' ', '-', '+', '.', '/', '\\'
    };

    private static final SparseBooleanArray numericCharDialableMap = new SparseBooleanArray (
            numericCharsDialable.length + numericCharsSugar.length);
    static {
        for (int i = 0; i < numericCharsDialable.length; i++) {
            numericCharDialableMap.put(numericCharsDialable[i], true);
        }
        for (int i = 0; i < numericCharsSugar.length; i++) {
            numericCharDialableMap.put(numericCharsSugar[i], false);
        }
    }

    /**
     * Given a numeric address string, return the string without
     * syntactic sugar, meaning parens, spaces, hyphens/minuses, or
     * plus signs.  If the input string contains non-numeric
     * non-punctuation characters, return null.
     */
    private static String filterNumericSugar(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();
        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);
            int mapIndex = numericCharDialableMap.indexOfKey(c);
            if (mapIndex < 0) return null;
            if (! numericCharDialableMap.valueAt(mapIndex)) continue;
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * Given a string, return the string without whitespace,
     * including CR/LF.
     */
    private static String filterWhitespace(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();
        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);
            if ((c == ' ') || (c == '\r') || (c == '\n') || (c == '\t')) continue;
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * Given a string, create a corresponding CdmaSmsAddress object.
     *
     * The result will be null if the input string is not
     * representable using printable ASCII.
     *
     * For numeric addresses, the string is cleaned up by removing
     * common punctuation.  For alpha addresses, the string is cleaned
     * up by removing whitespace.
     */
    public static CdmaSmsAddress parse(String address) {
        if (address == null) {
            Rlog.e(TAG, "address==null");
            return null;
        }

        CdmaSmsAddress addr = new CdmaSmsAddress();
        addr.address = address;
        addr.ton = TON_UNKNOWN;
        addr.numberPlan = NUMBERING_PLAN_UNKNOWN;

        byte[] origBytes = null;
        String filteredAddr = filterNumericSugar(address);

        if (address.indexOf('+') != -1) {
            // This is international phone number
            addr.ton = TON_INTERNATIONAL_OR_IP;
            addr.numberPlan = NUMBERING_PLAN_ISDN_TELEPHONY;
        } else if (address.indexOf('@') != -1) {
            // This is email address
            addr.ton = TON_NATIONAL_OR_EMAIL;
        } else {
            if (filteredAddr != null) {
                origBytes = parseToDtmf(filteredAddr);
            }
        }
        if (origBytes != null) {
            addr.digitMode = DIGIT_MODE_4BIT_DTMF;
            addr.numberMode = NUMBER_MODE_NOT_DATA_NETWORK;
        } else {
            addr.digitMode = DIGIT_MODE_8BIT_CHAR;
            addr.numberMode = NUMBER_MODE_DATA_NETWORK;
            // A.S0014-C 4.2.40 states: "Prefix or escape digits shall
            // not be included"; filterNumericSugar removes prefix and
            // filterWhitespace removes escape, whitespaces, etc.
            if (filteredAddr == null) {
                filteredAddr = filterWhitespace(address);
            }
            origBytes = UserData.stringToAscii(filteredAddr);
        }

        if (origBytes == null) {
            Rlog.d(TAG, "origBytes==null");
            return null;
        }

        addr.origBytes = origBytes;
        addr.numberOfDigits = origBytes.length;
        return addr;
    }

}
