import collections.HashSet;
import collections.Set;

import fs.FileStore;
import fs.Path;

/**
 * The Container service.
 *
 * Notes:
 * 1. Secure container (no shared singleton services)
 * 2. Lightweight container (all modules from the parent container are shared)
 *    - e.g. load some additional trusted code that you generated on the fly, Excel formula
 * 3. Debugger as a parent container
 *
 */
service Container
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a container based on the specified repository.
     */
    construct(ModuleRepository repository)
        {
        this.repository = repository;
        }

    /**
     * Construct a single module container.
     */
    construct(String moduleName, immutable Byte[] moduleBytes)
        {
        ModuleRepository simpleRepo = new ModuleRepository()
            {
            construct()
                {
                Set<String> names = new HashSet();
                names.add(moduleName);
                moduleNames = names.makeImmutable();
                }

            @Override
            immutable Byte[] getModule(String name)
                {
                assert(name == moduleName);

                return moduleBytes;
                }
            };

        construct Container(simpleRepo);
        }

    // ----- Container API -------------------------------------------------------------------------

    /**
     * The repository.
     */
    public/private ModuleRepository repository;

    /**
     * The resource provider.
     */
    ResourceProvider provider;

    /**
     * Load and verify the specified module.
     */
    ApplicationControl loadModule(String name)
        {
        TODO - native
        }

    // ----- interfaces ----------------------------------------------------------------------------

    /**
     * Represents the source of compiled module structures.
     */
    interface ModuleRepository()
        {
        /**
         * Set of domain names that are known by this repository.
         */
        @RO immutable Set<String> moduleNames;

        /**
         * Obtain a binary image of the specified module.
         */
        immutable Byte[] getModule(String name);
        }

    /**
     * Represents the source of injected resources.
     */
    interface ResourceProvider
        {
        /**
         * Obtain a resource for specified type and name. Most commonly, failure
         * a provider to return a resource (throwing an exception) will fail to load or
         * terminate the requesting container.
         */
        <Resource> Resource getResource(Type<Resource> type, String name);
        }

    /**
     * Represents the container control facility.
     */
    interface ApplicationControl
        {
        /**
         * Add a constraint for the specified name. The names are conventionally well known, for
         * example `memory`, `time interval`, `cpu cycles` `network bandwidth`.
         * TODO: there has to be a full section on the names and valid ranges
         */
        void addConstraint(String name, Interval<Int> interval);

        /**
         * Invoke the method with a given name and arguments.
         */
        Tuple invoke(String methodName, Tuple args);

        /**
         * Pause the application. This call will try a best effort attempt to stop the application
         * execution when it reaches a safe point (TODO: explain).
         */
        void pause();

        /**
         * Persist the application state to the specified FileStore. This operation is allowed only
         * after the application has been paused.
         */
        void flush(FileStore store);

        /**
         * Reload the application state from the specified FileStore and resume its execution.
         */
        void reactivate(FileStore store);

        /**
         * Kill the application immediately.
         */
        void kill();
        }
    }

