package io.frjufvjn.lab.vertx_mybatis.mysqlBinlog;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Guice;
import com.google.inject.Injector;

import io.frjufvjn.lab.vertx_mybatis.factory.VertxSqlConnectionFactory;
import io.frjufvjn.lab.vertx_mybatis.query.QueryModule;
import io.frjufvjn.lab.vertx_mybatis.query.QueryServices;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.sql.SQLConnection;

public class SqlServiceVerticle extends AbstractVerticle {
	private final Logger logger = LogManager.getLogger(SqlServiceVerticle.class);
	private String messageConsumerAddr = "msg.mysql.live.select.getsql";
	private Injector services = null;

	@Override
	public void start(Future<Void> startFuture) throws Exception {

		services = Guice.createInjector(new QueryModule());

		EventBus eb = vertx.eventBus();
		eb.consumer(messageConsumerAddr, msg -> {
			try {
				String sqlName = msg.body().toString();

				Map<String, Object> reqData = new LinkedHashMap<String, Object>();
				reqData.put("sqlName", sqlName);
				Map<String, Object> queryInfo = services.getInstance(QueryServices.class).getQuery(reqData);

				/* NOTE : There is no Asynchronous effect and it is sequential. 
				VertxSqlConnectionFactory.getClient().query((String)queryInfo.get("sql"), ar -> {
					if (ar.succeeded()) {
						msg.reply(ar.result().getRows().toString() );
					}
				});
				 */

				// this method is Asynchronous way even if there is a delay.
				VertxSqlConnectionFactory.getClient().getConnection(conn -> {
					if (conn.failed()) msg.reply("fail");

					try ( final SQLConnection connection = conn.result() ) {
						connection.query((String)queryInfo.get("sql"), ar -> {
							if(ar.succeeded()) msg.reply(ar.result().getRows().toString() );
							else msg.reply("fail");
						});
					}
				});
			} catch (Exception e) {
				msg.reply("fail");
			}

		}).completionHandler(ar -> {
			startFuture.complete();
			logger.info("SqlServiceVerticle EventBus Ready!! (address:"+messageConsumerAddr+")");
		});
	}
}
