/*
 * (C) Copyright 2014-2016 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.freeswitch.demoCall.sip;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Map of users registered in the system. This class has a concurrent hash map to store users, using
 * its name as key in the map.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @since 5.0.0
 */
@Log4j2
@Component
public class UserRegistry {

    private final ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println(System.currentTimeMillis() - 1693465784421L);
    }

    public int getSizeCalloutCalling() {
        return usersBySessionId.size();
    }

    public void register(UserSession user) {
        usersBySessionId.put(user.getSessionId(), user);
    }

    public UserSession getBySessionId(String id) {
        return usersBySessionId.get(id);
    }

    public boolean exists(String id) {
        return usersBySessionId.containsKey(id);
    }

    public UserSession removeBySessionId(String id) {
        final UserSession user = getBySessionId(id);
        if (user != null) {
            usersBySessionId.remove(id);
        }
        return user;
    }
}
