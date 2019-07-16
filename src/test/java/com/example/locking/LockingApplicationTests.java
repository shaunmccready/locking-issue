package com.example.locking;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest()
public class LockingApplicationTests {


    @Autowired
    private FileLockingService lockService;

    @Test
    public void baseLockTest() {
        List<Lockable> filesToProcess = new ArrayList<>();

        Lockable fileToLock = FileToLock.builder()
                .id(1)
                .build();

        filesToProcess.add(fileToLock);

        Runnable task = () -> {
            log.info("ATTEMPT LOCK");
            Set<BaseLockService.LockableHandle> lockedBatch = lockService.lockBatch(filesToProcess, 1);

            if (filesToProcess.size() == lockedBatch.size()) {

                try {
                    log.info("ATTEMPT SLEEP 1 MS");
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                log.info("ATTEMPT UNLOCK");
                lockService.unlockAll(lockedBatch);
            }
        };

        System.out.println("**********************************************************");
        System.out.println("**********************************************************");
        System.out.println("**********************************************************");

        int totalThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);


        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }


        executorService.shutdown();

        while (!executorService.isTerminated()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("ALL DONE!!!!!!!!");
    }
}
