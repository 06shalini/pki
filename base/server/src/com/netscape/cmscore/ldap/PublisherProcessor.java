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

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.dogtagpki.server.ca.ICertificateAuthority;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CRLImpl;

import com.netscape.certsrv.authority.ICertAuthority;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IConfigStore;
import com.netscape.certsrv.base.ISubsystem;
import com.netscape.certsrv.base.MetaInfo;
import com.netscape.certsrv.base.SessionContext;
import com.netscape.certsrv.dbs.Modification;
import com.netscape.certsrv.dbs.ModificationSet;
import com.netscape.certsrv.ldap.ELdapException;
import com.netscape.certsrv.ldap.ILdapConnModule;
import com.netscape.certsrv.publish.ILdapMapper;
import com.netscape.certsrv.publish.ILdapPublisher;
import com.netscape.certsrv.publish.IXcertPublisherProcessor;
import com.netscape.certsrv.publish.MapperPlugin;
import com.netscape.certsrv.publish.MapperProxy;
import com.netscape.certsrv.publish.PublisherPlugin;
import com.netscape.certsrv.publish.PublisherProxy;
import com.netscape.certsrv.publish.RulePlugin;
import com.netscape.certsrv.request.IRequest;
import com.netscape.certsrv.request.IRequestListener;
import com.netscape.certsrv.request.IRequestNotifier;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.dbs.CertRecord;
import com.netscape.cmscore.dbs.CertificateRepository;

import netscape.ldap.LDAPConnection;

/**
 * Controls the publishing process from the top level. Maintains
 * a collection of Publishers , Mappers, and Publish Rules.
 */
public class PublisherProcessor implements IXcertPublisherProcessor {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PublisherProcessor.class);

    public final static String PROP_LDAP_PUBLISH_SUBSTORE = "ldappublish";
    public final static String PROP_QUEUE_PUBLISH_SUBSTORE = "queue";

    public final static String PROP_LOCAL_CA = "cacert";
    public final static String PROP_LOCAL_CRL = "crl";
    public final static String PROP_CERTS = "certs";
    public final static String PROP_XCERT = "xcert";

    public final static String PROP_CLASS = "class";
    public final static String PROP_IMPL = "impl";
    public final static String PROP_PLUGIN = "pluginName";
    public final static String PROP_INSTANCE = "instance";

    public final static String PROP_PREDICATE = "predicate";
    public final static String PROP_ENABLE = "enable";
    public final static String PROP_CERT_ENABLE = "cert.enable";
    public final static String PROP_CRL_ENABLE = "crl.enable";
    public final static String PROP_LDAP = "ldap";
    public final static String PROP_MAPPER = "mapper";
    public final static String PROP_PUBLISHER = "publisher";
    public final static String PROP_TYPE = "type";

    public Hashtable<String, PublisherPlugin> mPublisherPlugins = new Hashtable<String, PublisherPlugin>();
    public Hashtable<String, PublisherProxy> mPublisherInsts = new Hashtable<String, PublisherProxy>();
    public Hashtable<String, MapperPlugin> mMapperPlugins = new Hashtable<String, MapperPlugin>();
    public Hashtable<String, MapperProxy> mMapperInsts = new Hashtable<String, MapperProxy>();
    public Hashtable<String, RulePlugin> mRulePlugins = new Hashtable<String, RulePlugin>();
    public Hashtable<String, LdapRule> mRuleInsts = new Hashtable<String, LdapRule>();

    // protected PublishRuleSet mRuleSet;

    protected LdapConnModule mLdapConnModule = null;

    private PublishingConfig mConfig;
    private IConfigStore mLdapConfig = null;
    private String mId = null;

    protected ICertAuthority mAuthority = null;
    protected IRequestListener requestListener;
    private boolean mCreateOwnDNEntry = false;
    private boolean mInited = false;

    public PublisherProcessor(String id) {
        mId = id;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public PublishingConfig getConfigStore() {
        return mConfig;
    }

    public IRequestListener getRequestListener() {
        return requestListener;
    }

    public void setRequestListener(IRequestListener requestListener) {
        this.requestListener = requestListener;
    }

    public void init(ISubsystem authority, PublishingConfig config) throws EBaseException {

        mConfig = config;
        mAuthority = (ICertAuthority) authority;

        PublishingPublisherConfig publisherConfig = config.getPublisherConfig();

        IConfigStore c = publisherConfig.getSubStore(PROP_IMPL);
        mCreateOwnDNEntry = mConfig.getBoolean("createOwnDNEntry", false);
        Enumeration<String> mImpls = c.getSubStoreNames();

        while (mImpls.hasMoreElements()) {
            String id = mImpls.nextElement();
            logger.info("PublisherProcessor: Loading publisher plugin " + id);

            String pluginPath = c.getString(id + "." + PROP_CLASS);
            PublisherPlugin plugin = new PublisherPlugin(id, pluginPath);
            mPublisherPlugins.put(id, plugin);
        }

        c = publisherConfig.getSubStore(PROP_INSTANCE);
        Enumeration<String> instances = c.getSubStoreNames();

        while (instances.hasMoreElements()) {
            String insName = instances.nextElement();
            logger.info("PublisherProcessor: Loading publisher instance " + insName);

            String implName = c.getString(insName + "." + PROP_PLUGIN);
            PublisherPlugin plugin = mPublisherPlugins.get(implName);

            if (plugin == null) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PLUGIN_NOT_FIND", implName));
                throw new ELdapException(implName);
            }

            String className = plugin.getClassPath();

            // Instantiate and init the publisher.
            boolean isEnable = false;
            ILdapPublisher publisherInst = null;

            try {
                publisherInst = (ILdapPublisher) Class.forName(className).newInstance();
                IConfigStore pConfig = c.getSubStore(insName);

                publisherInst.init(pConfig);
                isEnable = true;

            } catch (ClassNotFoundException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (IllegalAccessException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (InstantiationException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (Throwable e) {
                logger.warn("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_SKIP_PUBLISHER", insName, e.toString()), e);
                // Let the server continue if it is a
                // mis-configuration. But the instance
                // will be skipped. This give another
                // chance to the user to re-configure
                // the server via console.
            }

            if (publisherInst == null) {
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
            }

            if (insName == null) {
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", insName));
            }

            mPublisherInsts.put(insName, new PublisherProxy(isEnable, publisherInst));
        }

        IConfigStore mapperConfig = config.getSubStore("mapper");

        c = mapperConfig.getSubStore(PROP_IMPL);
        mImpls = c.getSubStoreNames();
        while (mImpls.hasMoreElements()) {
            String id = mImpls.nextElement();
            logger.info("PublisherProcessor: Loading mapper plugin " + id);

            String pluginPath = c.getString(id + "." + PROP_CLASS);
            MapperPlugin plugin = new MapperPlugin(id, pluginPath);
            mMapperPlugins.put(id, plugin);
        }

        c = mapperConfig.getSubStore(PROP_INSTANCE);
        instances = c.getSubStoreNames();
        while (instances.hasMoreElements()) {
            String insName = instances.nextElement();
            logger.info("PublisherProcessor: Loading mapper instance " + insName);

            String implName = c.getString(insName + "." + PROP_PLUGIN);
            MapperPlugin plugin = mMapperPlugins.get(implName);

            if (plugin == null) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_MAPPER_NOT_FIND", implName));
                throw new ELdapException(implName);
            }

            String className = plugin.getClassPath();

            // Instantiate and init the mapper
            boolean isEnable = false;
            ILdapMapper mapperInst = null;

            try {
                mapperInst = (ILdapMapper) Class.forName(className).newInstance();
                IConfigStore mConfig = c.getSubStore(insName);

                mapperInst.init(mConfig);
                isEnable = true;

            } catch (ClassNotFoundException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (IllegalAccessException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (InstantiationException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (Throwable e) {
                logger.warn("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_SKIP_MAPPER", insName, e.toString()), e);
                // Let the server continue if it is a
                // mis-configuration. But the instance
                // will be skipped. This give another
                // chance to the user to re-configure
                // the server via console.
            }

            if (mapperInst == null) {
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
            }

            mMapperInsts.put(insName, new MapperProxy(isEnable, mapperInst));
        }

        IConfigStore ruleConfig = config.getSubStore("rule");

        c = ruleConfig.getSubStore(PROP_IMPL);
        mImpls = c.getSubStoreNames();
        while (mImpls.hasMoreElements()) {
            String id = mImpls.nextElement();
            logger.info("PublisherProcessor: Loading rule plugin " + id);

            String pluginPath = c.getString(id + "." + PROP_CLASS);
            RulePlugin plugin = new RulePlugin(id, pluginPath);

            mRulePlugins.put(id, plugin);
        }

        c = ruleConfig.getSubStore(PROP_INSTANCE);
        instances = c.getSubStoreNames();
        while (instances.hasMoreElements()) {
            String insName = instances.nextElement();
            logger.info("PublisherProcessor: Loading rule instance " + insName);

            String implName = c.getString(insName + "." + PROP_PLUGIN);
            RulePlugin plugin = mRulePlugins.get(implName);

            if (plugin == null) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_RULE_NOT_FIND", implName));
                throw new ELdapException(implName);
            }

            String className = plugin.getClassPath();

            // Instantiate and init the rule
            IConfigStore mConfig = null;

            try {
                LdapRule ruleInst = null;

                ruleInst = (LdapRule)
                        Class.forName(className).newInstance();
                mConfig = c.getSubStore(insName);
                ruleInst.init(this, mConfig);
                ruleInst.setInstanceName(insName);

                mRuleInsts.put(insName, ruleInst);

            } catch (ClassNotFoundException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (IllegalAccessException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (InstantiationException e) {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PUBLISHER_INIT_FAILED", e.toString()), e);
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));

            } catch (Throwable e) {
                if (mConfig == null) {
                    throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
                }
                mConfig.putString(LdapRule.PROP_ENABLE, "false");
                logger.warn("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_SKIP_RULE", insName, e.toString()), e);
                // Let the server continue if it is a
                // mis-configuration. But the instance
                // will be skipped. This give another
                // chance to the user to re-configure
                // the server via console.
            }
        }

        startup();
        mInited = true;
    }

    /**
     * Returns LdapConnModule belonging to this Processor.
     *
     * @return LdapConnModule.
     */
    public ILdapConnModule getLdapConnModule() {
        return mLdapConnModule;
    }

    /**
     * Sets the LdapConnModule belonging to this Processor.
     *
     * @param m ILdapConnModule.
     */
    public void setLdapConnModule(ILdapConnModule m) {
        mLdapConnModule = (LdapConnModule) m;
    }

    /**
     * init ldap connection
     */
    private void initLdapConn(IConfigStore ldapConfig)
            throws EBaseException {
        IConfigStore c = ldapConfig;

        try {
            //c = authConfig.getSubStore(PROP_LDAP_PUBLISH_SUBSTORE);
            if (c != null && c.size() > 0) {
                mLdapConnModule = new LdapConnModule();
                mLdapConnModule.init(c);
                logger.debug("LdapPublishing connection inited");
            } else {
                logger.error("PublisherProcessor: No Ldap Module configuration found");
                throw new ELdapException(
                        CMS.getUserMessage("CMS_LDAP_NO_LDAP_PUBLISH_CONFIG_FOUND"));
            }

        } catch (ELdapException e) {
            logger.error("PublisherProcessor: Ldap Publishing Module failed: " + e.getMessage(), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_INIT_LDAP_PUBLISH_MODULE_FAILED", e.toString()));
        }
    }

    public void startup() throws EBaseException {
        logger.debug("PublisherProcessor: startup()");
        mLdapConfig = mConfig.getSubStore(PROP_LDAP_PUBLISH_SUBSTORE);
        if (mLdapConfig.getBoolean(PROP_ENABLE, false)) {
            logger.debug("PublisherProcessor: about to initLdapConn");
            initLdapConn(mLdapConfig);
        } else {
            logger.debug("No LdapPublishing enabled");
        }

        if (mConfig.getBoolean(PROP_ENABLE, false)) {

            if (mAuthority != null && requestListener != null) {
                mAuthority.registerRequestListener(requestListener);
            }

            IConfigStore queueConfig = mConfig.getSubStore(PROP_QUEUE_PUBLISH_SUBSTORE);
            if (queueConfig != null) {
                boolean isPublishingQueueEnabled = queueConfig.getBoolean("enable", false);
                int publishingQueuePriorityLevel = queueConfig.getInteger("priorityLevel", 0);
                int maxNumberOfPublishingThreads = queueConfig.getInteger("maxNumberOfThreads", 1);
                int publishingQueuePageSize = queueConfig.getInteger("pageSize", 100);
                int savePublishingStatus = queueConfig.getInteger("saveStatus", 0);
                logger.debug("PublisherProcessor: startup: Publishing Queue Enabled: " + isPublishingQueueEnabled +
                          "  Priority Level: " + publishingQueuePriorityLevel +
                          "  Maximum Number of Threads: " + maxNumberOfPublishingThreads +
                          "  Page Size: " + publishingQueuePageSize);
                IRequestNotifier reqNotifier = ((ICertificateAuthority) mAuthority).getRequestNotifier();
                reqNotifier.setPublishingQueue(isPublishingQueueEnabled,
                                                publishingQueuePriorityLevel,
                                                maxNumberOfPublishingThreads,
                                                publishingQueuePageSize,
                                                savePublishingStatus);
            }
        }
    }

    public void shutdown() {
        logger.debug("Shuting down publishing.");
        try {
            if (mLdapConnModule != null) {
                mLdapConnModule.getLdapConnFactory().reset();
            }
            if (mAuthority != null && requestListener != null) {
                //mLdapRequestListener.shutdown();
                mAuthority.removeRequestListener(requestListener);
            }
        } catch (ELdapException e) {
            // ignore
            logger.warn("Unable to shutdown publishing: " + e.getMessage(), e);
        }
    }

    /**
     * Returns Hashtable of rule plugins.
     */
    public Hashtable<String, RulePlugin> getRulePlugins() {
        return mRulePlugins;
    }

    /**
     * Returns Hashtable of rule instances.
     */
    public Hashtable<String, LdapRule> getRuleInsts() {
        return mRuleInsts;
    }

    /**
     * Returns Hashtable of mapper plugins.
     */
    public Hashtable<String, MapperPlugin> getMapperPlugins() {
        return mMapperPlugins;
    }

    /**
     * Returns Hashtable of publisher plugins.
     */
    public Hashtable<String, PublisherPlugin> getPublisherPlugins() {
        return mPublisherPlugins;
    }

    /**
     * Returns Hashtable of rule mapper instances.
     */
    public Hashtable<String, MapperProxy> getMapperInsts() {
        return mMapperInsts;
    }

    /**
     * Returns Hashtable of rule publisher instances.
     */
    public Hashtable<String, PublisherProxy> getPublisherInsts() {
        return mPublisherInsts;
    }

    /**
     * Returns list of rules based on publishing type.
     *
     * certType can be client,server,ca,crl,smime
     *
     * @param publishingType Type for which to retrieve rule list.
     */
    public Enumeration<LdapRule> getRules(String publishingType) {
        Vector<LdapRule> rules = new Vector<>();
        Enumeration<String> e = mRuleInsts.keys();

        while (e.hasMoreElements()) {
            String name = e.nextElement();

            if (name == null) {
                logger.trace("rule name is " + "null");
                return null;
            } else {
                logger.trace("rule name is " + name);
            }

            //this is the only rule we support now
            LdapRule rule = (mRuleInsts.get(name));

            if (rule.enabled() && publishingType.equals(rule.getType())) {
                // check if the predicate match
                ILdapExpression exp = rule.getPredicate();

                try {
                    SessionContext sc = SessionContext.getContext();

                    if (exp != null && !exp.evaluate(sc))
                        continue;
                } catch (Exception ex) {
                    // do nothing
                }
                rules.addElement(rule);
                logger.trace("added rule " + name + " for " + publishingType);
            }

        }
        return rules.elements();
    }

    /**
     * Returns list of rules based on publishing type and publishing request.
     *
     * @param publishingType Type for which to retrieve rule list.
     * @param req Corresponding publish request.
     */
    public Enumeration<LdapRule> getRules(String publishingType, IRequest req) {
        if (req == null) {
            return getRules(publishingType);
        }

        Vector<LdapRule> rules = new Vector<>();
        Enumeration<LdapRule> e = mRuleInsts.elements();

        while (e.hasMoreElements()) {
            //this is the only rule we support now
            LdapRule rule = e.nextElement();

            if (rule.enabled() && publishingType.equals(rule.getType())) {
                // check if the predicate match
                ILdapExpression exp = rule.getPredicate();

                try {
                    if (exp != null && !exp.evaluate(req))
                        continue;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                rules.addElement(rule);
                logger.trace("added rule " + rule.getInstanceName() + " for " + publishingType +
                            " request: " + req.getRequestId());
            }

        }
        return rules.elements();
    }

    // public PublishRuleSet getPublishRuleSet() {
    //     return mRuleSet;
    // }

    /**
     * Returns mapper initial default parameters.
     *
     * @param implName name of MapperPlugin.
     */
    public Vector<String> getMapperDefaultParams(String implName) throws
            ELdapException {
        // is this a registered implname?
        MapperPlugin plugin = mMapperPlugins.get(implName);

        if (plugin == null) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_MAPPER_NOT_FIND", implName));
            throw new ELdapException(implName);
        }

        // XXX can find an instance of this plugin in existing
        // mapper instances to avoid instantiation just for this.

        // a temporary instance
        ILdapMapper mapperInst = null;
        String className = plugin.getClassPath();

        try {
            mapperInst = (ILdapMapper)
                    Class.forName(className).newInstance();
            Vector<String> v = mapperInst.getDefaultParams();

            return v;
        } catch (InstantiationException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_MAPPER", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (ClassNotFoundException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_MAPPER", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (IllegalAccessException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_MAPPER", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        }
    }

    /**
     * Returns mapper current instance parameters.
     *
     * @param insName name of MapperProxy.
     * @exception ELdapException failed due to Ldap error.
     */
    public Vector<String> getMapperInstanceParams(String insName) throws
            ELdapException {
        ILdapMapper mapperInst = null;
        MapperProxy proxy = mMapperInsts.get(insName);

        if (proxy == null) {
            return null;
        }
        mapperInst = proxy.getMapper();
        if (mapperInst == null) {
            return null;
        }
        Vector<String> v = mapperInst.getInstanceParams();

        return v;
    }

    /**
     * Returns publisher initial default parameters.
     *
     * @param implName name of PublisherPlugin.
     * @exception ELdapException failed due to Ldap error.
     */
    public Vector<String> getPublisherDefaultParams(String implName) throws
            ELdapException {
        // is this a registered implname?
        PublisherPlugin plugin = mPublisherPlugins.get(implName);

        if (plugin == null) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_PLUGIN_NOT_FIND", implName));
            throw new ELdapException(implName);
        }

        // XXX can find an instance of this plugin in existing
        // publisher instantces to avoid instantiation just for this.

        // a temporary instance
        ILdapPublisher publisherInst = null;
        String className = plugin.getClassPath();

        try {
            publisherInst = (ILdapPublisher)
                    Class.forName(className).newInstance();
            Vector<String> v = publisherInst.getDefaultParams();

            return v;
        } catch (InstantiationException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_PUBLISHER", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (ClassNotFoundException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_PUBLISHER", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (IllegalAccessException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_PUBLISHER", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        }
    }

    /**
     * Returns true if MapperInstance is enabled.
     *
     * @param insName name of MapperProxy.
     * @return true if enabled. false if disabled.
     */
    public boolean isMapperInstanceEnable(String insName) {
        MapperProxy proxy = mMapperInsts.get(insName);

        if (proxy == null) {
            return false;
        }
        return proxy.isEnable();
    }

    /**
     * Returns ILdapMapper instance that is currently active.
     *
     * @param insName name of MapperProxy.
     * @return instance of ILdapMapper.
     */
    public ILdapMapper getActiveMapperInstance(String insName) {
        MapperProxy proxy = mMapperInsts.get(insName);

        if (proxy == null)
            return null;
        if (proxy.isEnable())
            return proxy.getMapper();
        else
            return null;
    }

    /**
     * Returns ILdapMapper instance based on name of MapperProxy.
     *
     * @param insName name of MapperProxy.
     * @return instance of ILdapMapper.
     */
    public ILdapMapper getMapperInstance(String insName) {
        MapperProxy proxy = mMapperInsts.get(insName);

        if (proxy == null)
            return null;
        return proxy.getMapper();
    }

    /**
     * Returns true publisher instance is currently enabled.
     *
     * @param insName name of PublisherProxy.
     * @return true if enabled.
     */
    public boolean isPublisherInstanceEnable(String insName) {
        PublisherProxy proxy = mPublisherInsts.get(insName);

        if (proxy == null) {
            return false;
        }
        return proxy.isEnable();
    }

    /**
     * Returns ILdapPublisher instance that is currently active.
     *
     * @param insName name of PublisherProxy.
     * @return instance of ILdapPublisher.
     */
    public ILdapPublisher getActivePublisherInstance(String insName) {
        PublisherProxy proxy = mPublisherInsts.get(insName);

        if (proxy == null) {
            return null;
        }
        if (proxy.isEnable())
            return proxy.getPublisher();
        else
            return null;
    }

    /**
     * Returns ILdapPublisher instance.
     *
     * @param insName name of PublisherProxy.
     * @return instance of ILdapPublisher.
     */
    public ILdapPublisher getPublisherInstance(String insName) {
        PublisherProxy proxy = mPublisherInsts.get(insName);

        if (proxy == null) {
            return null;
        }
        return proxy.getPublisher();
    }

    /**
     * Returns Vector of PublisherIntance's current instance parameters.
     *
     * @param insName name of PublisherProxy.
     * @return Vector of current instance parameters.
     */
    public Vector<String> getPublisherInstanceParams(String insName) throws
            ELdapException {
        ILdapPublisher publisherInst = getPublisherInstance(insName);

        if (publisherInst == null) {
            return null;
        }
        Vector<String> v = publisherInst.getInstanceParams();

        return v;
    }

    /**
     * Returns Vector of RulePlugin's initial default parameters.
     *
     * @param implName name of RulePlugin.
     * @return Vector of initial default parameters.
     * @exception ELdapException failed due to Ldap error.
     */
    public Vector<String> getRuleDefaultParams(String implName) throws
            ELdapException {
        // is this a registered implname?
        RulePlugin plugin = mRulePlugins.get(implName);

        if (plugin == null) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_RULE_NOT_FIND", implName));
            throw new ELdapException(implName);
        }

        // XXX can find an instance of this plugin in existing
        // rule instantces to avoid instantiation just for this.

        // a temporary instance
        LdapRule ruleInst = null;
        String className = plugin.getClassPath();

        try {
            ruleInst = (LdapRule) Class.forName(className).newInstance();

            Vector<String> v = ruleInst.getDefaultParams();

            return v;
        } catch (InstantiationException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_RULE", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (ClassNotFoundException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_RULE", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (IllegalAccessException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_RULE", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        }
    }

    /**
     * Returns Vector of RulePlugin's current instance parameters.
     *
     * @param implName name of RulePlugin.
     * @return Vector of current instance parameters.
     * @exception ELdapException failed due to Ldap error.
     */
    public Vector<String> getRuleInstanceParams(String implName) throws
            ELdapException {
        // is this a registered implname?
        RulePlugin plugin = mRulePlugins.get(implName);

        if (plugin == null) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_RULE_NOT_FIND", implName));
            throw new ELdapException(implName);
        }

        // XXX can find an instance of this plugin in existing
        // rule instantces to avoid instantiation just for this.

        // a temporary instance
        LdapRule ruleInst = null;
        String className = plugin.getClassPath();

        try {
            ruleInst = (LdapRule) Class.forName(className).newInstance();
            Vector<String> v = ruleInst.getInstanceParams();

            return v;
        } catch (InstantiationException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_RULE", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (ClassNotFoundException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_RULE", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        } catch (IllegalAccessException e) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_NEW_RULE", e.toString()), e);
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_FAIL_LOAD_CLASS", className));
        }
    }

    /**
     * Set published flag - true when published, false when unpublished.
     * Not exist means not published.
     *
     * @param serialNo serial number of publishable object.
     * @param published true for published, false for not.
     */
    public void setPublishedFlag(BigInteger serialNo, boolean published) {
        if (!(mAuthority instanceof ICertificateAuthority))
            return;
        ICertificateAuthority ca = (ICertificateAuthority) mAuthority;

        try {
            CertificateRepository certdb = ca.getCertificateRepository();
            CertRecord certRec = certdb.readCertificateRecord(serialNo);
            MetaInfo metaInfo = certRec.getMetaInfo();

            if (metaInfo == null) {
                metaInfo = new MetaInfo();
            }
            metaInfo.set(CertRecord.META_LDAPPUBLISH, String.valueOf(published));
            ModificationSet modSet = new ModificationSet();

            modSet.add(CertRecord.ATTR_META_INFO, Modification.MOD_REPLACE, metaInfo);
            certdb.modifyCertificateRecord(serialNo, modSet);

        } catch (EBaseException e) {
            // not fatal. just log warning.
            logger.warn("PublisherProcessor: Cannot mark cert 0x" + serialNo.toString(16)
                    + " published as " + published + " in the ldap directory.");
            logger.warn("PublisherProcessor: Cert Record not found: " + e.getMessage(), e);
            logger.warn("PublisherProcessor: Don't be alarmed if it's a subordinate ca or clone's ca siging cert.");
            logger.warn("PublisherProcessor: Otherwise your internal db may be corrupted.");
        }
    }

    /**
     * Publish ca cert, UpdateDir.java, jobs, request listeners
     *
     * @param cert X509 certificate to be published.
     * @exception ELdapException publish failed due to Ldap error.
     * @throws ELdapException
     */
    public void publishCACert(X509Certificate cert)
            throws ELdapException {
        boolean error = false;
        StringBuffer errorRule = new StringBuffer();

        if (!isCertPublishingEnabled())
            return;

        logger.debug("PublishProcessor::publishCACert");

        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules(PROP_LOCAL_CA);

        if (rules == null || !rules.hasMoreElements()) {
            if (isClone()) {
                logger.warn("PublisherProcessor: No rule is found for publishing: " + PROP_LOCAL_CA + " in this clone.");
                return;
            } else {
                logger.warn(CMS.getLogMessage("CMSCORE_LDAP_NO_RULE_FOUND", PROP_LOCAL_CA));
                //throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_RULE_MATCHED", PROP_LOCAL_CA));
                return;
            }
        }
        while (rules.hasMoreElements()) {
            LdapRule rule = rules.nextElement();

            if (rule == null) {
                logger.error("PublisherProcessor::publishCACert() - "
                         + "rule is null!");
                throw new ELdapException("rule is null");
            }

            logger.info("PublisherProcessor: publish certificate type=" + PROP_LOCAL_CA +
                    " rule=" + rule.getInstanceName() + " publisher=" +
                    rule.getPublisher());

            try {
                ILdapMapper mapper = null;

                String mapperName = rule.getMapper();

                if (mapperName != null &&
                        !mapperName.trim().equals("")) {
                    mapper = getActiveMapperInstance(mapperName);
                }

                publishNow(mapper, getActivePublisherInstance(rule.getPublisher()), null/* NO REQUEsT */, cert);
                logger.info("PublisherProcessor: published certificate using rule " + rule.getInstanceName());

            } catch (Exception e) {
                // continue publishing even publisher has errors
                logger.warn("PublisherProcessor::publishCACert returned error: " + e.getMessage(), e);
                error = true;
                errorRule.append(" " + rule.getInstanceName() + " error:" + e);
            }
        }
        // set the ldap published flag.
        if (!error) {
            setPublishedFlag(cert.getSerialNumber(), true);
        } else {
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_PUBLISH_FAILED", errorRule.toString()));
        }
    }

    /**
     * This function is never called. CMS does not unpublish
     * CA certificate.
     * @throws ELdapException
     */
    public void unpublishCACert(X509Certificate cert)
            throws ELdapException {
        boolean error = false;
        StringBuffer errorRule = new StringBuffer();

        if (!isCertPublishingEnabled())
            return;

        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules(PROP_LOCAL_CA);

        if (rules == null || !rules.hasMoreElements()) {
            if (isClone()) {
                logger.warn("PublisherProcessor: No rule is found for unpublishing: " + PROP_LOCAL_CA + " in this clone.");
                return;
            } else {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_UNPUBLISHING_RULE_FOUND", PROP_LOCAL_CA));
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_RULE_MATCHED", PROP_LOCAL_CA));
            }
        }

        while (rules.hasMoreElements()) {
            LdapRule rule = rules.nextElement();

            if (rule == null) {
                logger.error("PublisherProcessor::unpublishCACert() - "
                         + "rule is null!");
                throw new ELdapException("rule is null");
            }

            try {
                logger.info("PublisherProcessor: unpublish certificate type=" +
                        PROP_LOCAL_CA + " rule=" + rule.getInstanceName() +
                        " publisher=" + rule.getPublisher());

                ILdapMapper mapper = null;

                String mapperName = rule.getMapper();

                if (mapperName != null &&
                        !mapperName.trim().equals("")) {
                    mapper = getActiveMapperInstance(mapperName);
                }

                unpublishNow(mapper, getActivePublisherInstance(rule.getPublisher()), null/* NO REQUEST */, cert);
                logger.warn("PublisherProcessor: unpublished certificate using rule " + rule.getInstanceName());

            } catch (Exception e) {
                // continue publishing even publisher has errors
                logger.warn("PublisherProcessor: " + e.getMessage(), e);
                error = true;
                errorRule.append(" " + rule.getInstanceName());
            }
        }

        // set the ldap published flag.
        if (!error) {
            setPublishedFlag(cert.getSerialNumber(), false);
        } else {
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_UNPUBLISH_FAILED", errorRule.toString()));
        }
    }

    /**
     * Publish crossCertificatePair
     */
    public void publishXCertPair(byte[] pair)
            throws ELdapException {
        String errorRule = "";

        if (!isCertPublishingEnabled())
            return;
        logger.debug("PublisherProcessor: in publishXCertPair()");

        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules(PROP_XCERT);

        if (rules == null || !rules.hasMoreElements()) {
            if (isClone()) {
                logger.warn("PublisherProcessor: No rule is found for publishing: " + PROP_LOCAL_CA + " in this clone.");
                return;
            } else {
                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_RULE_FOUND", PROP_XCERT));
                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_RULE_MATCHED", PROP_XCERT));
            }
        }
        while (rules.hasMoreElements()) {
            LdapRule rule = rules.nextElement();

            if (rule == null) {
                logger.error("PublisherProcessor::publishXCertPair() - "
                         + "rule is null!");
                throw new ELdapException("rule is null");
            }

            logger.info("PublisherProcessor: publish certificate type=" + PROP_XCERT +
                    " rule=" + rule.getInstanceName() + " publisher=" +
                    rule.getPublisher());
            try {
                ILdapMapper mapper = null;

                String mapperName = rule.getMapper();

                if (mapperName != null &&
                        !mapperName.trim().equals("")) {
                    mapper = getActiveMapperInstance(mapperName);
                }

                publishNow(mapper, getActivePublisherInstance(rule.getPublisher()), null/* NO REQUEsT */, pair);
                logger.info("PublisherProcessor: published Xcertificates using rule " + rule.getInstanceName());

            } catch (Exception e) {
                // continue publishing even publisher has errors
                logger.warn("PublisherProcessor: " + e.getMessage(), e);
                errorRule = errorRule + " " + rule.getInstanceName() +
                        " error:" + e;

                logger.warn("PublisherProcessor::publishXCertPair: error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Publishs regular user certificate based on the criteria
     * set in the request.
     *
     * @param cert X509 certificate to be published.
     * @param req request which provides the criteria
     * @exception ELdapException publish failed due to Ldap error.
     * @throws ELdapException
     */
    public void publishCert(X509Certificate cert, IRequest req)
            throws ELdapException {
        boolean error = false;
        StringBuffer errorRule = new StringBuffer();

        logger.debug("In  PublisherProcessor::publishCert");
        if (!isCertPublishingEnabled())
            return;

        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules("certs", req);

        // Bugscape  #52306  -  Remove superfluous log messages on failure
        if (rules == null || !rules.hasMoreElements()) {
            logger.warn("Publishing: can't find publishing rule,exiting routine.");

            error = true;
            errorRule.append("No rules enabled");
        }

        while (rules != null && rules.hasMoreElements()) {
            LdapRule rule = rules.nextElement();

            try {
                logger.info("PublisherProcessor: publish certificate (with request) type=" +
                                "certs" + " rule=" + rule.getInstanceName() +
                                " publisher=" + rule.getPublisher());

                ILdapPublisher p = getActivePublisherInstance(rule.getPublisher());
                ILdapMapper m = null;
                String mapperName = rule.getMapper();

                if (mapperName != null) {
                    m = getActiveMapperInstance(mapperName);
                }
                publishNow(m, p, req, cert);

                logger.info("PublisherProcessor: published certificate using rule " + rule.getInstanceName());

            } catch (Exception e) {
                // continue publishing even publisher has errors
                logger.warn("PublisherProcessor: " + e.getMessage(), e);
                error = true;
                errorRule.append(" " + rule.getInstanceName());
            }
        }
        // set the ldap published flag.
        if (!error) {
            setPublishedFlag(cert.getSerialNumber(), true);
        } else {
            logger.error("PublishProcessor::publishCert : " + CMS.getUserMessage("CMS_LDAP_PUBLISH_FAILED", errorRule.toString()));
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_PUBLISH_FAILED", errorRule.toString()));
        }
    }

    /**
     * Unpublish user certificate. This is used by
     * UnpublishExpiredJob.
     *
     * @param cert X509 certificate to be unpublished.
     * @param req request which provides the criteria
     * @exception ELdapException unpublish failed due to Ldap error.
     * @throws ELdapException
     */
    public void unpublishCert(X509Certificate cert, IRequest req)
            throws ELdapException {
        boolean error = false;
        StringBuffer errorRule = new StringBuffer();

        if (!isCertPublishingEnabled())
            return;

        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules("certs", req);

        if (rules == null || !rules.hasMoreElements()) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_UNPUBLISHING_RULE_FOUND_FOR_REQUEST", "certs",
                    req.getRequestId().toString()));
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_RULE_MATCHED",
                    req.getRequestId().toString()));
        }

        while (rules.hasMoreElements()) {
            LdapRule rule = rules.nextElement();

            if (rule == null) {
                logger.error("PublisherProcessor::unpublishCert() - "
                         + "rule is null!");
                throw new ELdapException("rule is null");
            }

            try {
                logger.info("PublisherProcessor: unpublish certificate (with request) type=" +
                                "certs" + " rule=" + rule.getInstanceName() +
                                " publisher=" + rule.getPublisher());

                ILdapMapper mapper = null;

                String mapperName = rule.getMapper();

                if (mapperName != null &&
                        !mapperName.trim().equals("")) {
                    mapper = getActiveMapperInstance(mapperName);
                }

                unpublishNow(mapper, getActivePublisherInstance(rule.getPublisher()),
                        req, cert);
                logger.info("PublisherProcessor: unpublished certificate using rule " + rule.getInstanceName());

            } catch (Exception e) {
                // continue publishing even publisher has errors
                logger.warn("PublisherProcessor: " + e.getMessage(), e);
                error = true;
                errorRule.append(" " + rule.getInstanceName());
            }
        }

        // set the ldap published flag.
        if (!error) {
            setPublishedFlag(cert.getSerialNumber(), false);
        } else {
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_UNPUBLISH_FAILED", errorRule.toString()));
        }
    }

    /**
     * publishes a crl by mapping the issuer name in the crl to an entry
     * and publishing it there. entry must be a certificate authority.
     * Note that this is used by cmsgateway/cert/UpdateDir.java
     *
     * @param crl Certificate Revocation List
     * @param crlIssuingPointId name of the issuing point.
     * @exception ELdapException publish failed due to Ldap error.
     * @throws ELdapException
     */
    public void publishCRL(X509CRLImpl crl, String crlIssuingPointId)
            throws ELdapException {
        boolean error = false;
        String errorRule = "";

        if (!isCRLPublishingEnabled())
            return;
        ILdapMapper mapper = null;
        ILdapPublisher publisher = null;

        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules(PROP_LOCAL_CRL);

        if (rules == null || !rules.hasMoreElements()) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_RULE_FOR_CRL"));
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_RULE_MATCHED",
                    PROP_LOCAL_CRL));
        }

        LDAPConnection conn = null;
        String dn = null;

        try {
            if (mLdapConnModule != null) {
                conn = mLdapConnModule.getConn();
            }
            while (rules.hasMoreElements()) {
                mapper = null;
                dn = null;
                String result = null;
                LdapRule rule = rules.nextElement();

                logger.info("PublisherProcessor: publish crl rule=" +
                        rule.getInstanceName() + " publisher=" +
                        rule.getPublisher());
                try {
                    String mapperName = rule.getMapper();

                    if (mapperName != null &&
                            !mapperName.trim().equals("")) {
                        mapper = getActiveMapperInstance(mapperName);
                    }
                    if (mapper == null || mapper.getImplName().equals("NoMap")) {
                        dn = ((X500Name) crl.getIssuerDN()).toLdapDNString();
                    } else {

                        result = mapper.map(conn, crl);
                        dn = result;
                        if (!mCreateOwnDNEntry) {
                            if (dn == null) {
                                logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_MAPPER_NOT_MAP", rule.getMapper()));
                                throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_MATCH",
                                        crl.getIssuerDN().toString()));

                            }
                        }
                    }
                    publisher = getActivePublisherInstance(rule.getPublisher());
                    if (publisher != null) {
                        if (publisher instanceof com.netscape.cms.publish.publishers.FileBasedPublisher)
                            ((com.netscape.cms.publish.publishers.FileBasedPublisher) publisher)
                                    .setIssuingPointId(crlIssuingPointId);
                        publisher.publish(conn, dn, crl);
                        logger.info("PublisherProcessor: published crl using rule=" + rule.getInstanceName());
                    }
                    // continue publishing even publisher has errors
                } catch (Exception e) {
                    logger.warn("Error publishing CRL to " + dn + ": " + e.getMessage(), e);
                    error = true;
                    errorRule = errorRule + " " + rule.getInstanceName();
                }
            }

        } catch (ELdapException e) {
            logger.error("Error publishing CRL to " + dn + ": " + e.getMessage(), e);
            throw e;

        } finally {
            if (conn != null) {
                mLdapConnModule.returnConn(conn);
            }
        }
        if (error)
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_PUBLISH_FAILED", errorRule));
    }

    /**
     * publishes a crl by mapping the issuer name in the crl to an entry
     * and publishing it there. entry must be a certificate authority.
     *
     * @param dn Distinguished name to publish.
     * @param crl Certificate Revocation List
     * @exception ELdapException publish failed due to Ldap error.
     * @throws ELdapException
     */
    public void publishCRL(String dn, X509CRL crl)
            throws ELdapException {
        boolean error = false;
        String errorRule = "";

        if (!isCRLPublishingEnabled())
            return;
        // get mapper and publisher for cert type.
        Enumeration<LdapRule> rules = getRules(PROP_LOCAL_CRL);

        if (rules == null || !rules.hasMoreElements()) {
            logger.error("PublisherProcessor: " + CMS.getLogMessage("CMSCORE_LDAP_NO_RULE_FOR_CRL"));
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_RULE_MATCHED",
                    PROP_LOCAL_CRL));
        }

        LDAPConnection conn = null;
        ILdapPublisher publisher = null;

        try {
            if (mLdapConnModule != null) {
                conn = mLdapConnModule.getConn();
            }
            while (rules.hasMoreElements()) {
                LdapRule rule = rules.nextElement();

                logger.info("PublisherProcessor: publish crl dn=" + dn + " rule=" +
                        rule.getInstanceName() + " publisher=" +
                        rule.getPublisher());
                try {
                    publisher = getActivePublisherInstance(rule.getPublisher());
                    if (publisher != null) {
                        publisher.publish(conn, dn, crl);
                        logger.info("PublisherProcessor: published crl using rule=" + rule.getInstanceName());
                    }
                } catch (Exception e) {
                    logger.warn("Error publishing CRL to " + dn + ": " + e.getMessage(), e);
                    error = true;
                    errorRule = errorRule + " " + rule.getInstanceName();
                }
            }

        } catch (ELdapException e) {
            logger.error("Error publishing CRL to " + dn + ": " + e.getMessage(), e);
            throw e;

        } finally {
            if (conn != null) {
                mLdapConnModule.returnConn(conn);
            }
        }
        if (error)
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_PUBLISH_FAILED", errorRule));
    }

    private void publishNow(ILdapMapper mapper, ILdapPublisher publisher,
            IRequest r, Object obj) throws ELdapException {
        if (!isCertPublishingEnabled())
            return;
        logger.debug("PublisherProcessor: in publishNow()");
        LDAPConnection conn = null;

        try {
            Object dirdn = null;

            if (mapper != null) {
                if (mLdapConnModule != null) {
                    try {
                        conn = mLdapConnModule.getConn();
                    } catch (ELdapException e) {
                        throw e;
                    }
                }
                try {
                    if ((mapper instanceof com.netscape.cms.publish.mappers.LdapCertSubjMap) &&
                            ((com.netscape.cms.publish.mappers.LdapCertSubjMap) mapper).useAllEntries()) {
                        dirdn = ((com.netscape.cms.publish.mappers.LdapCertSubjMap) mapper).mapAll(conn, r, obj);
                    } else {
                        dirdn = mapper.map(conn, r, obj);
                    }
                } catch (Throwable e1) {
                    logger.error("Error mapping: mapper=" + mapper + " error=" + e1.getMessage(), e1);
                    throw e1;
                }
            }

            X509Certificate cert = (X509Certificate) obj;

            try {
                if (dirdn instanceof Vector) {
                    @SuppressWarnings("unchecked")
                    Vector<String> dirdnVector = (Vector<String>) dirdn;
                    int n = dirdnVector.size();
                    for (int i = 0; i < n; i++) {
                        publisher.publish(conn, dirdnVector.elementAt(i), cert);
                    }
                } else if (dirdn instanceof String ||
                           publisher instanceof com.netscape.cms.publish.publishers.FileBasedPublisher) {
                    publisher.publish(conn, (String) dirdn, cert);
                }
            } catch (Throwable e1) {
                logger.error("PublisherProcessor::publishNow : publisher=" + publisher + " error=" + e1.getMessage(), e1);
                throw e1;
            }
            logger.info("PublisherProcessor: published certificate serial number: 0x" +
                    cert.getSerialNumber().toString(16));
        } catch (ELdapException e) {
            throw e;
        } catch (Throwable e) {
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_MATCH", e.toString()));
        } finally {
            if (conn != null) {
                mLdapConnModule.returnConn(conn);
            }
        }
    }

    // for crosscerts
    private void publishNow(ILdapMapper mapper, ILdapPublisher publisher,
            IRequest r, byte[] bytes) throws EBaseException {
        if (!isCertPublishingEnabled())
            return;
        logger.debug("PublisherProcessor: in publishNow() for xcerts");

        // use ca cert publishing map and rule
        ICertificateAuthority ca = (ICertificateAuthority) mAuthority;
        X509Certificate caCert = ca.getCACert();

        LDAPConnection conn = null;

        try {
            String dirdn = null;

            if (mapper != null) {
                if (mLdapConnModule != null) {
                    conn = mLdapConnModule.getConn();
                }
                try {
                    dirdn = mapper.map(conn, r, caCert);
                    logger.debug("PublisherProcessor: dirdn=" + dirdn);

                } catch (Throwable e1) {
                    logger.error("Error mapping: mapper=" + mapper + " error=" + e1.getMessage(), e1);
                    throw e1;
                }
            }

            try {
                logger.debug("PublisherProcessor: publisher impl name=" + publisher.getImplName());

                publisher.publish(conn, dirdn, bytes);
            } catch (Throwable e1) {
                logger.error("Error publishing: publisher=" + publisher + " error=" + e1.getMessage(), e1);
                throw e1;
            }

            logger.info("PublisherProcessor: published crossCertPair");

        } catch (ELdapException e) {
            throw e;
        } catch (Throwable e) {
            throw new ELdapException(CMS.getUserMessage("CMS_LDAP_NO_MATCH", e.toString()));
        } finally {
            if (conn != null) {
                mLdapConnModule.returnConn(conn);
            }
        }
    }

    private void unpublishNow(ILdapMapper mapper, ILdapPublisher publisher,
            IRequest r, Object obj) throws ELdapException {
        if (!isCertPublishingEnabled())
            return;
        LDAPConnection conn = null;

        try {
            String dirdn = null;

            if (mapper != null) {
                if (mLdapConnModule != null) {
                    conn = mLdapConnModule.getConn();
                }
                dirdn = mapper.map(conn, r, obj);
            }
            X509Certificate cert = (X509Certificate) obj;

            publisher.unpublish(conn, dirdn, cert);

            logger.info("PublisherProcessor: unpublished certificate serial number: 0x" +
                    cert.getSerialNumber().toString(16));

        } catch (ELdapException e) {
            throw e;
        } finally {
            if (conn != null) {
                mLdapConnModule.returnConn(conn);
            }
        }
    }

    /**
     * Return true if Ldap is enabled.
     *
     * @return true if Ldap is enabled,otherwise false.
     */
    public boolean ldapEnabled() {
        try {
            if (mInited)
                return mLdapConfig.getBoolean(PROP_ENABLE, false);
            else
                return false;
        } catch (EBaseException e) {
            return false;
        }
    }

    /**
     * Return true if Certificate Publishing is enabled.
     * @return true if enabled, false otherwise
     */
    public boolean isCertPublishingEnabled() {
        if (!mInited) return false;
        try {
            if (!mConfig.getBoolean(PROP_ENABLE, false)) return false;
            return mConfig.getBoolean(PROP_CERT_ENABLE, true);
        } catch (EBaseException e) {
            // this should never happen
            logger.error("Error getting publishing config: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Return true if CRL publishing is enabled,
     * @return true if enabled,  false otherwise.
     */
    public boolean isCRLPublishingEnabled() {
        if (!mInited) return false;
        try {
            if (!mConfig.getBoolean(PROP_ENABLE, false)) return false;
            return mConfig.getBoolean(PROP_CRL_ENABLE, true);
        } catch (EBaseException e) {
            // this should never happen
            logger.error("Error getting publishing config: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Return Authority for which this Processor operates.
     *
     * @return Authority.
     */
    public ISubsystem getAuthority() {
        return mAuthority;
    }

    public boolean isClone() {
        if ((mAuthority instanceof ICertificateAuthority) &&
                ((ICertificateAuthority) mAuthority).isClone())
            return true;
        else
            return false;
    }
}
