// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cmscore.ldap;

import java.security.cert.Certificate;
import java.util.Hashtable;

import org.mozilla.jss.netscape.security.x509.X509CertImpl;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IConfigStore;
import com.netscape.certsrv.base.ISubsystem;
import com.netscape.certsrv.ldap.ELdapException;
import com.netscape.certsrv.request.IRequest;
import com.netscape.certsrv.request.IRequestListener;
import com.netscape.cms.profile.common.EnrollProfile;
import com.netscape.cmscore.apps.CMS;

public class LdapRequestListener implements IRequestListener {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LdapRequestListener.class);

    /**
     * handlers for request types (events)
     * each handler implement IRequestListener
     */
    private Hashtable<String, IRequestListener> mRequestListeners = new Hashtable<String, IRequestListener>();

    public LdapRequestListener() {
    }

    public void set(String name, String val) {
    }

    public void setPublisherProcessor(PublisherProcessor publisherProcessor) {

        mRequestListeners.put(IRequest.ENROLLMENT_REQUEST,
                new LdapEnrollmentListener(publisherProcessor));

        mRequestListeners.put(IRequest.RENEWAL_REQUEST,
                new LdapRenewalListener(publisherProcessor));

        mRequestListeners.put(IRequest.REVOCATION_REQUEST,
                new LdapRevocationListener(publisherProcessor));

        mRequestListeners.put(IRequest.UNREVOCATION_REQUEST,
                new LdapUnrevocationListener(publisherProcessor));
    }

    public void init(ISubsystem sys, IConfigStore config) throws EBaseException {
    }

    public PublishObject getPublishObject(IRequest r) {
        String type = r.getRequestType();
        PublishObject obj = new PublishObject();

        if (type.equals(IRequest.ENROLLMENT_REQUEST)) {
            // in case it's not meant for us
            if (r.getExtDataInInteger(IRequest.RESULT) == null)
                return null;

            // check if request failed.
            if ((r.getExtDataInInteger(IRequest.RESULT)).equals(IRequest.RES_ERROR)) {
                logger.warn("Request errored. " +
                        "Nothing to publish for enrollment request id " +
                        r.getRequestId());
                return null;
            }
            logger.debug("Checking publishing for request " + r.getRequestId());
            // check if issued certs is set.
            X509CertImpl[] certs = r.getExtDataInCertArray(IRequest.ISSUED_CERTS);

            if (certs == null || certs.length == 0 || certs[0] == null) {
                logger.warn("No certs to publish for request id " + r.getRequestId());
                return null;
            }
            obj.setCerts(certs);
            return obj;
        } else if (type.equals(IRequest.RENEWAL_REQUEST)) {
            // Note we do not remove old certs from directory during renewal
            X509CertImpl[] certs = r.getExtDataInCertArray(IRequest.ISSUED_CERTS);

            if (certs == null || certs.length == 0) {
                logger.warn("no certs to publish for renewal " +
                        "request " + r.getRequestId());
                return null;
            }
            obj.setCerts(certs);
            return obj;
        } else if (type.equals(IRequest.REVOCATION_REQUEST)) {
            X509CertImpl[] revcerts = r.getExtDataInCertArray(IRequest.OLD_CERTS);

            if (revcerts == null || revcerts.length == 0 || revcerts[0] == null) {
                // no certs in revoke.
                logger.warn("Nothing to unpublish for revocation " +
                                "request " + r.getRequestId());
                return null;
            }
            obj.setCerts(revcerts);
            return obj;
        } else if (type.equals(IRequest.UNREVOCATION_REQUEST)) {
            X509CertImpl[] certs = r.getExtDataInCertArray(IRequest.OLD_CERTS);

            if (certs == null || certs.length == 0 || certs[0] == null) {
                // no certs in unrevoke.
                logger.warn("Nothing to publish for unrevocation " +
                                "request " + r.getRequestId());
                return null;
            }
            obj.setCerts(certs);
            return obj;
        } else {
            logger.warn("Request errored. " +
                    "Nothing to publish for request id " +
                    r.getRequestId());
            return null;
        }

    }

    public void accept(IRequest r) {
        String type = r.getRequestType();

        IRequestListener handler = mRequestListeners.get(type);

        if (handler == null) {
            logger.warn("Nothing to publish for request type " + type);
            return;
        }
        handler.accept(r);
    }

}

class LdapEnrollmentListener implements IRequestListener {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LdapEnrollmentListener.class);

    PublisherProcessor mProcessor = null;

    public LdapEnrollmentListener(PublisherProcessor processor) {
        mProcessor = processor;
    }

    public void init(ISubsystem sys, IConfigStore config) throws EBaseException {
    }

    public void set(String name, String val) {
    }

    public void accept(IRequest r) {
        logger.debug("LdapRequestListener handling publishing for enrollment request id " +
                        r.getRequestId());

        String profileId = r.getExtDataInString(IRequest.PROFILE_ID);

        if (profileId == null) {
            // in case it's not meant for us
            if (r.getExtDataInInteger(IRequest.RESULT) == null)
                return;

            // check if request failed.
            if ((r.getExtDataInInteger(IRequest.RESULT)).equals(IRequest.RES_ERROR)) {
                logger.warn("Request errored. " +
                        "Nothing to publish for enrollment request id " +
                        r.getRequestId());
                return;
            }
        }
        logger.debug("Checking publishing for request " +
                r.getRequestId());
        // check if issued certs is set.
        Certificate[] certs = null;
        if (profileId == null) {
            certs = r.getExtDataInCertArray(IRequest.ISSUED_CERTS);
        } else {
            certs = new Certificate[1];
            certs[0] = r.getExtDataInCert(EnrollProfile.REQUEST_ISSUED_CERT);
        }

        if (certs == null || certs.length == 0 || certs[0] == null) {
            logger.warn("No certs to publish for request id " + r.getRequestId());
            return;
        }

        if (certs[0] instanceof X509CertImpl)
            acceptX509(r, certs);
    }

    public void acceptX509(IRequest r, Certificate[] certs) {
        Integer results[] = new Integer[certs.length];
        boolean error = false;

        for (int i = 0; i < certs.length; i++) {
            X509CertImpl xcert = (X509CertImpl) certs[i];

            if (xcert == null)
                continue;
            try {
                mProcessor.publishCert(xcert, r);

                results[i] = IRequest.RES_SUCCESS;
                logger.debug("acceptX509: Published cert serial no 0x" +
                                xcert.getSerialNumber().toString(16));
                //mProcessor.setPublishedFlag(xcert.getSerialNumber(), true);
            } catch (ELdapException e) {
                logger.warn(CMS.getLogMessage("CMSCORE_LDAP_CERT_NOT_PUBLISH",
                                xcert.getSerialNumber().toString(16), e.toString()), e);
                results[i] = IRequest.RES_ERROR;
                error = true;
            }
        }
        r.setExtData("ldapPublishStatus", results);
        r.setExtData("ldapPublishOverAllStatus",
                (error == true ? IRequest.RES_ERROR : IRequest.RES_SUCCESS));
    }
}
