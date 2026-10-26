package com.meditationmap.notification.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 트랜잭션이 있으면 커밋 뒤에, 없으면 즉시 실행한다. 실시간 전송이 롤백된 상태를 내보내지 않게 하는 용도. */
public final class AfterCommit {

    private AfterCommit() {}

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
            return;
        }
        task.run();
    }
}
