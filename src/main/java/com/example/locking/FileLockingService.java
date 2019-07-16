package com.example.locking;

import org.springframework.stereotype.Service;

@Service
public class FileLockingService extends BaseLockService {

    /**
     * The node path used by the {@link org.apache.curator.framework.recipes.locks.InterProcessMutex}.
     */
    private static final String LOCK_PATH = "/myApp/files";

    @Override
    protected String getLockPath() {
        return LOCK_PATH;
    }
}

