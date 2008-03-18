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
package com.netscape.cms.policy.constraints;


import org.mozilla.jss.crypto.*;
import java.util.*;
import com.netscape.certsrv.request.IRequest;
import com.netscape.certsrv.request.PolicyResult;
import com.netscape.certsrv.policy.*;
import com.netscape.certsrv.ca.*;
import com.netscape.certsrv.base.*;
import com.netscape.certsrv.security.*;
import com.netscape.certsrv.authority.*;
import com.netscape.certsrv.common.*;
import com.netscape.certsrv.logging.*;
import com.netscape.certsrv.apps.*;
import netscape.security.x509.*;
import com.netscape.cms.policy.APolicyRule;


/**
 * This simple policy checks the subordinate CA CSR to see
 * if it is the same as the local CA.
 *
 * @version $Revision: 14561 $, $Date: 2007-05-01 10:28:56 -0700 (Tue, 01 May 2007) $
 */
public class SubCANameConstraints extends APolicyRule implements IEnrollmentPolicy, IExtendedPluginInfo {
    public ICertificateAuthority mCA = null;
    public String mIssuerNameStr = null;

    public SubCANameConstraints() {
        NAME = "SubCANameConstraints";
        DESC = "Enforces Subordinate CA name.";
    }

    public String[] getExtendedPluginInfo(Locale locale) {
        String[] params = {
                IExtendedPluginInfo.HELP_TOKEN +
                ";configuration-policyrules-subcanamecheck",
                IExtendedPluginInfo.HELP_TEXT +
                ";Checks if subordinate CA request matches the local CA. There are no parameters to change"
            };

        return params;

    }
	
    /**
     * Initializes this policy rule.
     * <P>
     *
     * The entries probably are of the form
     *      ra.Policy.rule.<ruleName>.implName=KeyAlgorithmConstraints
     *      ra.Policy.rule.<ruleName>.algorithms=RSA,DSA
     *      ra.Policy.rule.<ruleName>.enable=true
     *      ra.Policy.rule.<ruleName>.predicate=ou==Sales
     *
     * @param config	The config store reference
     */
    public void init(ISubsystem owner, IConfigStore config)
        throws EBaseException {
        // get CA's public key to create authority key id.
        ICertAuthority certAuthority = (ICertAuthority) 
            ((IPolicyProcessor) owner).getAuthority();

        if (certAuthority == null) {
            // should never get here.
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CA_CANT_FIND_MANAGER"));
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INTERNAL_ERROR",
                        "Cannot find the Certificate Manager"));
        }
        if (!(certAuthority instanceof ICertificateAuthority)) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CA_CANT_FIND_MANAGER"));
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INTERNAL_ERROR",
                        "Cannot find the Certificate Manager"));
        }
        mCA = (ICertificateAuthority) certAuthority;
        ISigningUnit su = mCA.getSigningUnit();
        if( su == null || CMS.isPreOpMode() ) {
            return;
        }

        X509CertImpl cert = su.getCertImpl();

        if (cert == null)
            return;
        X500Name issuerName = (X500Name) cert.getSubjectDN();

        if (issuerName == null)
            return;
        mIssuerNameStr = issuerName.toString();
    }

    /**
     * Applies the policy on the given Request.
     * <P>
     *
     * @param req	The request on which to apply policy.
     * @return The policy result object.
     */
    public PolicyResult apply(IRequest req) {
        PolicyResult result = PolicyResult.ACCEPTED;

        try {

            // Get the certificate templates
            X509CertInfo[] certInfos = req.getExtDataInCertInfoArray(
                    IRequest.CERT_INFO);
						
            if (certInfos == null) {
                log(ILogger.LL_FAILURE, CMS.getLogMessage("POLICY_NO_CERT_INFO", getInstanceName()));
                setError(req, CMS.getUserMessage("CMS_POLICY_NO_CERT_INFO", NAME + ":" + getInstanceName()), "");
                return PolicyResult.REJECTED;
            }

            // retrieve the subject name and check its unqiueness
            for (int i = 0; i < certInfos.length; i++) {
                CertificateSubjectName subName = (CertificateSubjectName) certInfos[i].get(X509CertInfo.SUBJECT);

                // if there is no name set, set one here.
                if (subName == null) {
                    log(ILogger.LL_FAILURE, CMS.getLogMessage("POLICY_NO_SUBJECT_NAME_1", getInstanceName()));
                    setError(req, CMS.getUserMessage("CMS_POLICY_NO_SUBJECT_NAME", NAME + ":" + getInstanceName()), "");
                    return PolicyResult.REJECTED;
                }
                String certSubjectName = subName.toString();

                if (certSubjectName.equalsIgnoreCase(mIssuerNameStr)) {
                    log(ILogger.LL_FAILURE, CMS.getLogMessage("POLICY_SUBJECT_NAME_EXIST_1", mIssuerNameStr));
                    setError(req, CMS.getUserMessage("CMS_POLICY_SUBJECT_NAME_EXIST", NAME + ":" + "Same As Issuer Name " + mIssuerNameStr), "");
                    result = PolicyResult.REJECTED;
                }
            }
        } catch (Exception e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("POLICY_NO_SUBJECT_NAME_1", getInstanceName()));
            String params[] = {getInstanceName(), e.toString()};

            setError(req, CMS.getUserMessage("CMS_POLICY_UNEXPECTED_POLICY_ERROR",
                    params), "");
            result = PolicyResult.REJECTED;
        }
        return result;
    }

    /**
     * Return configured parameters for a policy rule instance.
     *
     * @return nvPairs A Vector of name/value pairs.
     */
    public Vector getInstanceParams() { 
        Vector v = new Vector();

        return v;
    }
		
    /**
     * Return default parameters for a policy implementation.
     *
     * @return nvPairs A Vector of name/value pairs.
     */
    public Vector getDefaultParams() { 
        Vector v = new Vector();

        return v;
    }
}

