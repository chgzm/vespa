// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Upgrades instances in manually deployed zones to the system version, at a convenient time.
 * 
 * @author jonmv
 */
public class DeploymentUpgrader extends ControllerMaintainer {

    public DeploymentUpgrader(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        Versions target = new Versions(controller().readSystemVersion(), ApplicationVersion.unknown, Optional.empty(), Optional.empty());
        for (Application application : controller().applications().readable())
            for (Instance instance : application.instances().values())
                for (Deployment deployment : instance.deployments().values())
                    try {
                        attempts.incrementAndGet();
                        JobId job = new JobId(instance.id(), JobType.from(controller().system(), deployment.zone()).get());
                        if ( ! deployment.zone().environment().isManuallyDeployed()) continue;
                        if ( ! deployment.version().isBefore(target.targetPlatform())) continue;
                        if (   controller().clock().instant().isBefore(controller().jobController().last(job).get().start().plus(Duration.ofDays(1)))) continue;
                        if ( ! isLikelyNightFor(job)) continue;

                        log.log(Level.FINE, "Upgrading deployment of " + instance.id() + " in " + deployment.zone());
                        controller().jobController().start(instance.id(), JobType.from(controller().system(), deployment.zone()).get(), target, true);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        log.log(Level.WARNING, "Failed upgrading " + deployment + " of " + instance +
                                               ": " + Exceptions.toMessageString(e) + ". Retrying in " +
                                               interval());
                    }
        return asSuccessFactor(attempts.get(), failures.get());
    }

    private boolean isLikelyNightFor(JobId job) {
        int hour = hourOf(controller().clock().instant());
        int[] runStarts = controller().jobController().runs(job).descendingMap().values().stream()
                                      .filter(run -> ! run.isRedeployment())
                                      .mapToInt(run -> hourOf(run.start()))
                                      .toArray();
        int localNight = mostLikelyWeeHour(runStarts);
        return Math.abs(hour - localNight) <= 1;
    }

    static int mostLikelyWeeHour(int[] starts) {
        double weight = 1; // Weight more recent deployments higher.
        double[] buckets = new double[24];
        for (int start : starts)
            buckets[start] += weight *= (Math.sqrt(5) - 1) * 0.5; // When in doubt, use the golden ratio.

        int best = -1;
        double min = Double.MAX_VALUE;
        for (int i = 12; i < 36; i++) {
            double sum = 0;
            for (int j = -12; j < 12; j++)
                sum += Math.abs(j) * buckets[(i + j) % 24];

            if (sum < min) {
                min = sum;
                best = i;
            }
        }
        return (best + 13) % 24; // rot13 of weighted average deployment start is likely in the middle of the night.
    }

    private static int hourOf(Instant instant) {
        return (int) (instant.toEpochMilli() / 3_600_000 % 24);
    }

}
