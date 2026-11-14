package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.store.JobExecution;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRow;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Occupancy from the jobs stored in the PostgreSQL of the dev services, on the application's clock. */
class OccupancyServiceIntegrationTest {

    private static final long RUNNER = 1219L;

    @BeforeEach
    void twoJobsOnARunnerOfCapacityTwo() {
        InProcessApp.truncate("runner", "runner_status_change", "job_execution");
        RunnerRow r = new RunnerRow();
        r.setId(RUNNER);
        r.setOrg("Vidocq");
        r.setUuid("f924a289-a095-41cb-aebb-326899bb485b");
        r.setName("CodefloeRunnerMac");
        r.setLastStatus("active");
        r.setCapacityOverride(2);
        InProcessApp.bean(RunnerRowRepository.class).save(r);
        Instant now = Instant.now();
        JobExecutionRepository jobs = InProcessApp.bean(JobExecutionRepository.class);
        jobs.save(job(1, now.minus(Duration.ofMinutes(50)), now.minus(Duration.ofMinutes(20))));
        jobs.save(job(2, now.minus(Duration.ofMinutes(40)), now.minus(Duration.ofMinutes(10))));
    }

    private static JobExecution job(long task, Instant started, Instant stopped) {
        JobExecution e = new JobExecution();
        e.setTaskId(task);
        e.setJobId(task + 1000);
        e.setRunId(7L);
        e.setOrg("Vidocq");
        e.setRepo("vauban");
        e.setRunnerId(RUNNER);
        e.setStatus("success");
        e.setStartedAt(started);
        e.setStoppedAt(stopped);
        return e;
    }

    @Test
    void twoHalfHourJobsFillHalfOfTwoSlotsOverAnHour() {
        double ratio = InProcessApp.bean(OccupancyService.class).occupancy("Vidocq", Duration.ofHours(1))
                .get(RUNNER).slotRatio();
        assertEquals(0.5, ratio, 0.01);
    }

    @Test
    void hourlyHasTwentyFourValuesTheLastHourLast() {
        List<Double> hours = InProcessApp.bean(OccupancyService.class).hourly("Vidocq").get(RUNNER);
        assertEquals(24, hours.size());
        assertEquals(0.5, hours.getLast(), 0.01);
        assertEquals(0.0, hours.getFirst(), 0.0);
    }
}
