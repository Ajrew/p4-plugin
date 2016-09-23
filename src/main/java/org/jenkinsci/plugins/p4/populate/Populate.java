package org.jenkinsci.plugins.p4.populate;

import java.io.Serializable;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

public abstract class Populate implements ExtensionPoint, Describable<Populate>, Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean have; // ! sync '-p'
	private final boolean force; // sync '-f'
	private final boolean modtime;
	private final boolean quiet; // task '-q'
	private final String pin;
        private final String viewMask;
	private final ParallelSync parallel;

	public Populate(boolean have, boolean force, boolean modtime, boolean quiet, String pin, String viewMask, ParallelSync parallel) {
		this.have = have;
		this.force = force;
		this.modtime = modtime;
		this.pin = pin;
                this.viewMask = viewMask;
		this.quiet = quiet;
		this.parallel = parallel;
	}

	public boolean isHave() {
		return have;
	}

	public boolean isForce() {
		return force;
	}

	public boolean isModtime() {
		return modtime;
	}

	public boolean isQuiet() {
		return quiet;
	}

	public String getPin() {
		return pin;
	}
        public String getViewMask() {
            return viewMask;
        }

	public ParallelSync getParallel() {
		return parallel;
	}

	public PopulateDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (PopulateDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<Populate, PopulateDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<Populate, PopulateDescriptor> getDescriptorList(Populate.class);
		}
		return null;
	}
}
