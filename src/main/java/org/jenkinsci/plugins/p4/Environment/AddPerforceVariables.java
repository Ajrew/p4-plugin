/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.p4.Environment;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
/**
 *
 * @author jussi.kossi
 */
@Extension
public class AddPerforceVariables extends EnvironmentContributor {
    
    public static String changeList;
    
    @Override
    public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException 
    {
       if (this.changeList != null ) {
           envs.put("P4_CHANGELIST", this.changeList);
       }      
    }
    
    
    public void SetChangelist(String changeList)
    {
        this.changeList = changeList;
    }
    
    public String GetChangeList()
    {
        return this.changeList;
    }
    
    
}
