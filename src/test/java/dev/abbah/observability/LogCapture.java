package dev.abbah.observability;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the records the application logs during each test, with their MDC, through a handler
 * on the root logger. Register it with {@code @RegisterExtension}.
 */
public class LogCapture implements BeforeEachCallback, AfterEachCallback {

    private final List<ExtLogRecord> records = new CopyOnWriteArrayList<>();

    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            ExtLogRecord extRecord = ExtLogRecord.wrap(record);
            // Snapshots the MDC and the formatted message of the logging thread.
            extRecord.copyAll();
            records.add(extRecord);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @Override
    public void beforeEach(ExtensionContext context) {
        records.clear();
        Logger.getLogger("").addHandler(handler);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Logger.getLogger("").removeHandler(handler);
    }

    public List<ExtLogRecord> records() {
        return List.copyOf(records);
    }
}
