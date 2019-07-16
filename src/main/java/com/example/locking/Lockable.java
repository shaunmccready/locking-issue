package com.example.locking;

public interface Lockable {

    /**
     * The ID of the object (normally the primary key). This id will be used as a handle for the lock. It will link the
     * {@link Lockable} object to its lock without having a reference.
     *
     * @return The ID of the {@link Lockable} object.
     */
    int getId();
}