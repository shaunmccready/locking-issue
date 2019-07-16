package com.example.locking;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FileToLock implements Lockable {

    private int id;

    @Override
    public int getId(){
     return id;
    }

}
