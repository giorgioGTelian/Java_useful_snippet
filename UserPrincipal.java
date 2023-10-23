/**
 * Retrieves or creates a service user.
 * Should be overriden by subclasses wanting to rely on a different field as key.
 */
protected String getOrCreateServiceUser(HttpServletRequest request, String accessToken) throws IOException {
    String nuxeoLogin = request.getUserPrincipal().getName();
    String userId = getServiceUserId(nuxeoLogin);
    if (userId == null) {
        userId = getServiceUserStore().store(nuxeoLogin);
    }
    return userId;
}


@Override
public UddiEntityPublisher identify(String authInfoNotused, String authorizedNameNotused, WebServiceContext ctx)
        throws AuthenticationException, FatalErrorException {
    int MaxBindingsPerService = -1;
    int MaxServicesPerBusiness = -1;
    int MaxTmodels = -1;
    int MaxBusinesses = -1;
    try {/*from   ww w  .j a  v  a2  s .com*/
        MaxBindingsPerService = AppConfig.getConfiguration().getInt(Property.JUDDI_MAX_BINDINGS_PER_SERVICE,
                -1);
        MaxServicesPerBusiness = AppConfig.getConfiguration().getInt(Property.JUDDI_MAX_SERVICES_PER_BUSINESS,
                -1);
        MaxTmodels = AppConfig.getConfiguration().getInt(Property.JUDDI_MAX_TMODELS_PER_PUBLISHER, -1);
        MaxBusinesses = AppConfig.getConfiguration().getInt(Property.JUDDI_MAX_BUSINESSES_PER_PUBLISHER, -1);
    } catch (Exception ex) {
        MaxBindingsPerService = -1;
        MaxServicesPerBusiness = -1;
        MaxTmodels = -1;
        MaxBusinesses = -1;
        log.error("config exception! ", ex);
    }
    EntityManager em = PersistenceManager.getEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
        String user = null;
        if (ctx == null)
            throw new UnknownUserException(
                    new ErrorMessage("errors.auth.NoPublisher", "no web service context!"));
        if (ctx.getUserPrincipal() != null) {
            user = ctx.getUserPrincipal().getName();
        }
        if (user == null) {
            MessageContext mc = ctx.getMessageContext();
            HttpServletRequest req = null;
            if (mc != null) {
                req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);
            }
            if (req != null && req.getUserPrincipal() != null) {
                user = req.getUserPrincipal().getName();
            }
        }
        if (user == null || user.length() == 0) {
            throw new UnknownUserException(new ErrorMessage("errors.auth.NoPublisher"));
        }
        tx.begin();
        Publisher publisher = em.find(Publisher.class, user);
        if (publisher == null) {
            log.warn("Publisher \"" + user
                    + "\" was not found in the database, adding the publisher in on the fly.");
            publisher = new Publisher();
            publisher.setAuthorizedName(user);
            publisher.setIsAdmin("false");
            publisher.setIsEnabled("true");
            publisher.setMaxBindingsPerService(MaxBindingsPerService);
            publisher.setMaxBusinesses(MaxBusinesses);
            publisher.setMaxServicesPerBusiness(MaxServicesPerBusiness);
            publisher.setMaxTmodels(MaxTmodels);
            publisher.setPublisherName("Unknown");
            em.persist(publisher);
            tx.commit();
        }

        return publisher;
    } finally {
        if (tx.isActive()) {
            tx.rollback();
        }
        em.close();
    }
}


@Override
public void invoke(ValveContext context) throws ContainerException {
    HttpServletRequest request = context.getServletRequest();
    Principal userPrincipal = request.getUserPrincipal();

    // If user has not been authenticated yet by any mechanism, then simply move to the next valve chain.
    if (userPrincipal == null) {
        if (log.isDebugEnabled()) {
            log.debug("No user principal found. Skipping SpringSecurityValve...");
        }// w  w  w  .j  a  va2  s.co m
        context.invokeNext();
        return;
    }

    // Get the current subject from http session if exists.
    HttpSession session = request.getSession(false);
    Subject subject = (session != null ? (Subject) session.getAttribute(ContainerConstants.SUBJECT_ATTR_NAME)
            : null);

    // If a subject has been established already (normally by HST-2's SecurityValve), then simply move to the next valve chain.
    if (subject != null) {
        if (log.isDebugEnabled()) {
            log.debug("Already subject has been created somewhere before. Skipping SpringSecurityValve...");
        }
        context.invokeNext();
        return;
    }

    // Get Spring Security Context object from thread local.
    SecurityContext securityContext = SecurityContextHolder.getContext();

    // If there's no Spring Security Context object, then just move to next valve chain.
    if (securityContext == null) {
        if (log.isDebugEnabled()) {
            log.debug("Spring Security hasn't established security context. Skipping SpringSecurityValve...");
        }
        context.invokeNext();
        return;
    }

    // Get the Authentication object from the Spring Security context object.
    Authentication authentication = securityContext.getAuthentication();

    // If there's no Authentication object, it's really weird, so leave warning logs, and move to next valve chain.
    if (authentication == null) {
        if (log.isWarnEnabled()) {
            log.warn(
                    "Spring Security hasn't establish security context with authentication object. Skipping SpringSecurityValve...");
        }
        context.invokeNext();
        return;
    }

    // Get principal object from the Spring Security authentication object.
    Object springSecurityPrincipal = authentication.getPrincipal();

    // We expect the principal is instance of UserDetails. Otherwise, let's skip it and leave warning logs.
    if (!(springSecurityPrincipal instanceof UserDetails)) {
        if (log.isWarnEnabled()) {
            log.warn(
                    "Spring Security hasn't establish security context with UserDetails object. We don't support non UserDetails authentication. Skipping SpringSecurityValve...");
        }
        context.invokeNext();
        return;
    }

    // Cast principal instance to UserDetails 
    UserDetails userDetails = (UserDetails) springSecurityPrincipal;

    // Create HST-2 TransientUser principal from the user principal.
    User user = new TransientUser(userPrincipal.getName());

    // Add both the existing user principal and new HST-2 user transient user principal
    // just for the case when HST-2 can inspect the user principals for some reasons.
    Set<Principal> principals = new HashSet<Principal>();
    principals.add(userPrincipal);
    principals.add(user);

    // Retrieve all the granted authorities from the UserDetail instance
    // and convert it into HST-2 TransientRoles.
    for (GrantedAuthority authority : userDetails.getAuthorities()) {
        String authorityName = authority.getAuthority();
        if (!StringUtils.isEmpty(authorityName)) {
            principals.add(new TransientRole(authorityName));
        }
    }

    Set<Object> pubCred = new HashSet<Object>();
    Set<Object> privCred = new HashSet<Object>();

    // If the flag is turned on, then store JCR credentials as well
    // just for the case the site is expected to use session stateful JCR sessions per authentication.
    if (storeSubjectRepositoryCredentials) {
        Credentials subjectRepoCreds = null;

        // Note: password should be null by default from some moment after Spring Security version upgraded a while ago.
        //       if password is null, let's store a dummy password instead.

        if (userDetails.getPassword() != null) {
            subjectRepoCreds = new SimpleCredentials(userDetails.getUsername(),
                    userDetails.getPassword().toCharArray());
        } else {
            subjectRepoCreds = new SimpleCredentials(userDetails.getUsername(), DUMMY_CHARS);
        }

        privCred.add(subjectRepoCreds);
    }

    subject = new Subject(true, principals, pubCred, privCred);

    // Save the created subject as http session attribute which can be read by HST-2 SecurityValve in the next valve chain.
    request.getSession(true).setAttribute(ContainerConstants.SUBJECT_ATTR_NAME, subject);

    context.invokeNext();
}
