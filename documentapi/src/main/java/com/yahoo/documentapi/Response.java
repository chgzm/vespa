// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import java.util.Objects;

import static com.yahoo.documentapi.Response.Outcome.ERROR;
import static com.yahoo.documentapi.Response.Outcome.SUCCESS;

/**
 * <p>An asynchronous response from the document api.
 * Subclasses of this provide additional response information for particular operations.</p>
 *
 * <p>This is a <i>value object</i>.</p>
 *
 * @author bratseth
 */
public class Response {

    private final long requestId;
    private final String textMessage;
    private final Outcome outcome;

    /** Creates a successful response containing no information */
    public Response(long requestId) {
        this(requestId, null);
    }

    /**
     * Creates a successful response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     */
    public Response(long requestId, String textMessage) {
        this(requestId, textMessage, SUCCESS);
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param success     true if the response represents a successful call
     */
    @Deprecated(since = "7") // TODO: Remove on Vespa 8
    public Response(long requestId, String textMessage, boolean success) {
        this(requestId, textMessage, success ? SUCCESS : ERROR);
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param outcome     the outcome of the operation
     */
    public Response(long requestId, String textMessage, Outcome outcome) {
        this.requestId = requestId;
        this.textMessage = textMessage;
        this.outcome = outcome;
    }

    /**
     * Returns the text message of this response or null if there is none
     *
     * @return the message, or null
     */
    public String getTextMessage() { return textMessage; }

    /**
     * Returns whether this response encodes a success or a failure
     *
     * @return true if success
     */
    public boolean isSuccess() { return outcome == SUCCESS; }

    /** Returns the outcome of this operation. */
    public Outcome outcome() { return outcome; }

    public long getRequestId() { return requestId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Response)) return false;
        Response response = (Response) o;
        return requestId == response.requestId &&
               Objects.equals(textMessage, response.textMessage) &&
               outcome == response.outcome;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, textMessage, outcome);
    }

    public String toString() {
        return "Response " + requestId + (textMessage == null ? "" : textMessage) + " " + outcome;
    }


    public enum Outcome {

        /** The operation was a success. */
        SUCCESS,

        /** The operation failed due to an unmet test-and-set condition. */
        CONDITION_FAILED,

        /** The operation failed because its target document was not found. */
        NOT_FOUND,

        /** The operation failed for some unknown reason. */
        ERROR

    }

}
