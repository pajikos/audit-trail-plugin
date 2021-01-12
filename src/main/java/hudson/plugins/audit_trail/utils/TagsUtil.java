/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package hudson.plugins.audit_trail.utils;

import net.sf.json.JSONArray;

import java.util.*;

public class TagsUtil {

    public static Map<String, Set<String>> merge(Map<String, Set<String>> dest, Map<String, Set<String>> orig) {
        if (dest == null) {
            dest = new HashMap<>();
        }
        if (orig == null) {
            orig = new HashMap<>();
        }
        for (final Iterator<Map.Entry<String, Set<String>>> iter = orig.entrySet().iterator(); iter.hasNext();){
            Map.Entry<String, Set<String>> entry = iter.next();
            final String oName = entry.getKey();
            Set<String> dValues = dest.containsKey(oName) ? dest.get(oName) : new HashSet<String>();
            if (dValues == null) {
                dValues = new HashSet<>();
            }
            Set<String> oValues = entry.getValue();
            if (oValues != null) {
                dValues.addAll(oValues);
            }
            dest.put(oName, dValues);
        }
        return dest;
    }

    public static JSONArray convertTagsToJSONArray(Map<String, Set<String>> tags){
        JSONArray result = new JSONArray();
        for (final Iterator<Map.Entry<String, Set<String>>> iter = tags.entrySet().iterator(); iter.hasNext();){
            Map.Entry<String, Set<String>> entry = iter.next();
            String name = entry.getKey();
            Set<String> values = entry.getValue();
            for (String value : values){
                if ("".equals(value)){
                    result.add(name); // Tag with no value
                }else{
                    result.add(String.format("%s:%s", name, value));
                }
            }
        }
        return result;
    }

    public static String[] convertTagsToArray(Map<String, Set<String>> tags){
        List<String> result = new ArrayList<>();
        for (final Iterator<Map.Entry<String, Set<String>>> iter = tags.entrySet().iterator(); iter.hasNext();){
            Map.Entry<String, Set<String>> entry = iter.next();
            String name = entry.getKey();
            Set<String> values = entry.getValue();
            for (String value : values){
                if("".equals(value)){
                    result.add(name);
                }else{
                    result.add(String.format("%s:%s", name, value));
                }
            }
        }
        Collections.sort(result);
        return result.toArray(new String[0]);
    }

    public static Map<String,Set<String>> addTagToTags(Map<String, Set<String>> tags, String name, String value) {
        if(tags == null){
            tags = new HashMap<>();
        }
        if(!tags.containsKey(name)){
            tags.put(name, new HashSet<>());
        }
        tags.get(name).add(value);
        return tags;
    }
}