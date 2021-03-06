/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.edurt.gcm.quartz.scheduler;

import java.util.Properties;

public class SchedulerConfiguration
        implements SchedulerConfigurationBuilder
{
    private boolean manualStart = false;
    private Properties properties;

    @Override
    public SchedulerConfigurationBuilder withManualStart()
    {
        manualStart = true;
        return this;
    }

    @Override
    public SchedulerConfigurationBuilder withProperties(Properties properties)
    {
        this.properties = properties;
        return this;
    }

    public boolean startManually()
    {
        return manualStart;
    }

    public Properties getProperties()
    {
        return properties;
    }
}
