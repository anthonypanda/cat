package com.dianping.cat.report.page.model.failure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

import com.dianping.cat.consumer.failure.model.entity.FailureReport;
import com.dianping.cat.consumer.failure.model.transform.DefaultMerger;
import com.dianping.cat.report.page.model.spi.ModelRequest;
import com.dianping.cat.report.page.model.spi.ModelResponse;
import com.dianping.cat.report.page.model.spi.ModelService;
import com.site.lookup.annotation.Inject;

public class CompositeFailureModelService implements ModelService<FailureReport>, Initializable {
	@Inject
	private List<ModelService<FailureReport>> m_services;

	private ExecutorService m_threadPool;

	@Override
	public void initialize() throws InitializationException {
		m_threadPool = Executors.newFixedThreadPool(10);
	}

	@Override
	public ModelResponse<FailureReport> invoke(final ModelRequest request) {
		int size = m_services.size();
		final List<ModelResponse<FailureReport>> responses = new ArrayList<ModelResponse<FailureReport>>(size);
		final CountDownLatch latch = new CountDownLatch(size);

		for (final ModelService<FailureReport> service : m_services) {
			m_threadPool.submit(new Runnable() {
				@Override
				public void run() {
					try {
						responses.add(service.invoke(request));
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				}
			});
		}

		try {
			latch.await(5000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// ignore it
		}

		ModelResponse<FailureReport> aggregated = new ModelResponse<FailureReport>();
		DefaultMerger merger = null;

		for (ModelResponse<FailureReport> response : responses) {
			if (response != null) {
				FailureReport model = response.getModel();

				if (model != null) {
					if (merger == null) {
						merger = new DefaultMerger(model);
					} else {
						model.accept(merger);
					}
				}
			}
		}

		aggregated.setModel(merger == null ? null : merger.getFailureReport());
		return aggregated;
	}

	@Override
	public boolean isEligable(ModelRequest request) {
		for (ModelService<FailureReport> service : m_services) {
			if (service.isEligable(request)) {
				return true;
			}
		}

		return false;
	}

	public void setSerivces(ModelService<FailureReport>... services) {
		m_services = Arrays.asList(services);
	}
}
