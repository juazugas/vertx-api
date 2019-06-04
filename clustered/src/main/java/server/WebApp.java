package server;

import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.cluster.infinispan.ClusterHealthCheck;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;

public class WebApp extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(WebApp.class.getName());


    @Override
    public void start(Future<Void> startFuture) throws Exception {

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Locale.setDefault(Locale.US);
		Router router = Router.router(vertx);
        setupHealthCheck(router);

        int serverPort = Optional.ofNullable(System.getProperty("server.port")).map(Integer::valueOf).orElse(8080);
        logger.debug("listening on port:" + serverPort);
        vertx.createHttpServer().requestHandler(router).listen(serverPort);
        startFuture.complete();
    }

    public static void main(String[] args) {
        /*
         * Runner.runExample(Server.class, new
         * DeploymentOptions().setWorker(true).setWorkerPoolSize(40).
         * setMaxWorkerExecuteTime(3000));
         */
        VertxOptions vo = new VertxOptions();
        vo.getEventBusOptions().setClustered(true);
        Vertx.clusteredVertx(vo, ar -> {
            if (ar.failed()) {
                logger.error("Cannot create vert.x instance : " + ar.cause());
			} else {
				Vertx vertx = ar.result();
				vertx.deployVerticle(WebApp.class.getName());
			}
		});
    }
        
    private void setupHealthCheck (Router router) {
		Handler<Future<Status>> procedure = ClusterHealthCheck.createProcedure(vertx, true);
		HealthChecks checks = HealthChecks.create(vertx).register("cluster-health", procedure);

		router.get("/health*").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks.create(vertx)));
		router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));

		vertx.eventBus().consumer("health", message -> checks.invoke(message::reply));
   }

}