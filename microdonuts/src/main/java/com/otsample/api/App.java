package com.otsample.api;

import java.io.IOException;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.*;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options;

public class App
{
    public static void main( String[] args )
        throws Exception
    {
        Properties config = loadConfig(args);
        if (!configureGlobalTracer(config, "dronutz"))
            throw new Exception("Could not configure the global tracer");

        ResourceHandler filesHandler = new ResourceHandler();
        filesHandler.setWelcomeFiles(new String[]{ "./index.html" });
        filesHandler.setResourceBase(config.getProperty("public_directory"));

        ContextHandler fileCtxHandler = new ContextHandler();
        fileCtxHandler.setHandler(filesHandler);

        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.setHandlers(new Handler[] {
            fileCtxHandler,
            new ApiContextHandler(config),
            new KitchenContextHandler(config),
        });
        Server server = new Server(10001);
        server.setHandler(handlers);

        server.start();
        server.dumpStdErr ();
        server.join();
    }

    static Properties loadConfig(String [] args)
        throws IOException
    {
        String file = "config_example.properties";
        if (args.length > 0)
            file = args[0];

        FileInputStream fs = new FileInputStream(file);
        Properties config = new Properties();
        config.load(fs);
        return config;
    }

    static boolean configureGlobalTracer(Properties config, String componentName)
        throws MalformedURLException
    {
        String tracerName = config.getProperty("tracer");
        if ("jaeger".equals(tracerName)) {
            GlobalTracer.register(
                new com.uber.jaeger.Configuration(
                    componentName,
                    new com.uber.jaeger.Configuration.SamplerConfiguration("const", 1),
                    new com.uber.jaeger.Configuration.ReporterConfiguration(
                        true,  // logSpans
                        config.getProperty("jaeger.reporter_host"),
                        Integer.decode(config.getProperty("jaeger.reporter_port")),
                        1000,   // flush interval in milliseconds
                        10000)  // max buffered Spans
                ).getTracer());
        } else if ("lightstep".equals(tracerName)) {
            Options opts = new Options.OptionsBuilder()
                .withAccessToken(config.getProperty("lightstep.access_token"))
                .withCollectorHost(config.getProperty("lightstep.collector_host"))
                .withCollectorPort(Integer.decode(config.getProperty("lightstep.collector_port")))
                .withComponentName(componentName)
                .build();
            Tracer tracer = new JRETracer(opts);
            GlobalTracer.register(tracer);
        } else {
            return false;
        }
        GlobalTracer.get().buildSpan("testing").start().setTag("blah", "foo").finish();

        return true;
    }
}
