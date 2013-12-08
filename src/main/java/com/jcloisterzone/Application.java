package com.jcloisterzone;

/**
 * Various application constants.
 * @author Roman Krejcik
 */
public interface Application {

    public String VERSION = "2.5";
    public String BUILD_DATE = "2013-12-08";

    public int PROTCOL_VERSION = 16; //2.5

    public static final String ILLEGAL_STATE_MSG = "Method '{}' called in invalid state";
}
