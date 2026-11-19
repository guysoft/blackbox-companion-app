// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

public final class HelloRetryTest {
    public static void main(String[] args) {
        HelloRetry retry = new HelloRetry();
        check(!retry.canRetry(1, 0));
        retry.begin(100);
        check(retry.canRetry(1, 100));
        check(retry.canRetry(1, 10100 - 1));
        check(!retry.canRetry(1, 10100));
        for (int cmd = 2; cmd <= 12; cmd++) check(!retry.canRetry(cmd, 500));
        retry.clear();
        check(!retry.canRetry(1, 500));
        retry.begin(20000);
        check(retry.canRetry(1, 20500));
        check(!retry.canRetry(1, 30000));
        System.out.println("Bounded HELLO-only startup retry tests passed");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
