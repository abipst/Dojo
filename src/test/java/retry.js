function retryUntilTrue(condition, timeout, interval) {
    var start = new Date().getTime();
    while (new Date().getTime() - start < timeout) {
        if (condition()) {
            return true;
        }
        karate.sleep(interval);
    }
    return false;
}