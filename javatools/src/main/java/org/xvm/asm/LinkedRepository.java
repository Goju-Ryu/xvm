package org.xvm.asm;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


/**
 * A repository that delegates to a chain of repositories. Reads occur from the repositories in the
 * order that they were provided to the constructor. Writes occur to the first repository only.
 */
public class LinkedRepository
        implements ModuleRepository
    {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a LinkedRepository.
     *
     * @param repos  a sequence of repositories to use, in order, to search through
     */
    public LinkedRepository(ModuleRepository... repos)
        {
        this(false, repos);
        }

    /**
     * Construct a LinkedRepository.
     *
     * @param fReadThrough  pass true to store a copy of all read modules in the first repository
     * @param repos         a sequence of repositories to use, in order, to search through
     */
    public LinkedRepository(boolean fReadThrough, ModuleRepository... repos)
        {
        assert repos != null && repos.length > 0;
        for (ModuleRepository repo : repos)
            {
            assert repo != null;
            }

        this.repos       = repos.clone();
        this.readThrough = fReadThrough;
        }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return a list of the repositories that underlie this repository
     */
    public List<ModuleRepository> asList()
        {
        return Collections.unmodifiableList(Arrays.asList(repos));
        }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getDomainNames()
        {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            names.addAll(repo.getDomainNames());
            }
        return names;
        }

    @Override
    public Set<String> getModuleNames(String sDomain)
        {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            names.addAll(repo.getModuleNames(sDomain));
            }
        return names;
        }

    @Override
    public Set<String> getModuleNames()
        {
        TreeSet<String> names = new TreeSet<>();
        for (ModuleRepository repo : repos)
            {
            names.addAll(repo.getModuleNames());
            }
        return names;
        }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String sModule)
        {
        VersionTree<Boolean> vers = new VersionTree<>();
        for (ModuleRepository repo : repos)
            {
            for (Version ver : repo.getAvailableVersions(sModule))
                {
                vers.put(ver, true);
                }
            }
        return vers;
        }

    @Override
    public ModuleStructure loadModule(String sModule)
        {
        for (int i = 0, c = repos.length; i < c; ++i)
            {
            ModuleRepository repo   = repos[i];
            ModuleStructure  module = repo.loadModule(sModule);
            if (module != null)
                {
                // technically we could automatically merge this module with all the other versions
                // found in all of the other repositories; the choice at this point is to defer
                // that work

                if (i > 0 && readThrough)
                    {
                    repos[0].storeModule(module);
                    }

                return module;
                }
            }
        return null;
        }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact)
        {
        for (int i = 0, c = repos.length; i < c; ++i)
            {
            ModuleRepository repo   = repos[i];
            ModuleStructure  module = repo.loadModule(sModule, version, fExact);
            if (module != null)
                {
                if (i > 0 && readThrough)
                    {
                    repos[0].storeModule(module);
                    }

                return module;
                }
            }
        return null;
        }

    @Override
    public void storeModule(ModuleStructure module)
        {
        repos[0].storeModule(module);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A sequence of repositories to use, in order, to search through. All writes occur to the first
     * repository in the array.
     */
    private final ModuleRepository[] repos;

    /**
     * A value of true stores a copy of all read modules in the first repository.
     */
    private final boolean            readThrough;
    }
