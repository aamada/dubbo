/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.remoting.zookeeper.support;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractZookeeperClient<TargetChildListener> implements ZookeeperClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractZookeeperClient.class);

    /**
     * 注册中心URL
     */
    private final URL url;

    /**
     * 状态监听器
     */
    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<StateListener>();

    /**
     * 子节点监听器
     */
    private final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();

    /**
     * 是否关闭
     */
    private volatile boolean closed = false;

    /**
     * 永久存在的节点
     */
    private final Set<String>  persistentExistNodePath = new ConcurrentHashSet<String>();

    /**
     * 构造方法
     *
     * @param url 注册中心URL
     */
    public AbstractZookeeperClient(URL url) {
        this.url = url;
    }

    // ---------------------------------------------------------StateListener相关方法
    @Override
    public URL getUrl() {
        // 注册中心的URL
        return url;
    }


    @Override
    public void delete(String path){
        //never mind if ephemeral
        // 从集合中删除
        persistentExistNodePath.remove(path);
        // 去删除节点路径
        // 留给子类实现
        deletePath(path);
    }


    /**
     * 创建节点路径
     *
     * @param path 节点路径
     * @param ephemeral 临时节点?
     */
    @Override
    public void create(String path, boolean ephemeral) {
        if (!ephemeral) {
            // 不是临时节点 -> 永久节点
            // 永久节点是否包含这个节点呢?
            if(persistentExistNodePath.contains(path)){
                // 如果已经包含, 那么直接返回
                return;
            }
            // 这个节点是否存在?
            // 留给子类实现, 为什么呢?
            if (checkExists(path)) {
                // 不存在的话, 那么放入至集合中去
                persistentExistNodePath.add(path);
                // 返回
                return;
            }
        }
        // 那么到了处理临时节点了
        // 最后一个"/"的index
        int i = path.lastIndexOf('/');
        if (i > 0) {
            // 如果有这个"/", 那么循环创建节点
            // 先去创建父路径
            create(path.substring(0, i), false);
        }
        if (ephemeral) {
            // 是临时节点, 那么创建临时节点
            // 留给子类实现
            createEphemeral(path);
        } else {
            // 创建永久节点
            // 留给子类实现
            createPersistent(path);
            // 将之放入至集合中去
            persistentExistNodePath.add(path);

        }
    }

    @Override
    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }

    @Override
    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }



    @Override
    public List<String> addChildListener(String path, final ChildListener listener) {
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners == null) {
            // 放入至容器中
            childListeners.putIfAbsent(path, new ConcurrentHashMap<ChildListener, TargetChildListener>());
            // 拿到这个路径的监听器
            listeners = childListeners.get(path);
        }
        TargetChildListener targetListener = listeners.get(listener);
        if (targetListener == null) {
            listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
            targetListener = listeners.get(listener);
        }
        // 向zk发起真正的订阅
        return addTargetChildListener(path, targetListener);
    }

    @Override
    public void removeChildListener(String path, ChildListener listener) {
        // 找到某个路径下的监听器
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners != null) {
            // 如果监听器不为null
            //  将监听器移除掉
            TargetChildListener targetListener = listeners.remove(listener);
            if (targetListener != null) {
                // 走
                // 留给子类去实现
                removeTargetChildListener(path, targetListener);
            }
        }
    }
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            doClose();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }
    // ---------------------------------------------------------StateListener相关方法
    public Set<StateListener> getSessionListeners() {
        return stateListeners;
    }
    protected void stateChanged(int state) {
        // 遍历状态监听器
        for (StateListener sessionListener : getSessionListeners()) {
            // 回调方法
            sessionListener.stateChanged(state);
        }
    }


    protected abstract void doClose();

    protected abstract void createPersistent(String path);

    protected abstract void createEphemeral(String path);

    protected abstract boolean checkExists(String path);

    protected abstract TargetChildListener createTargetChildListener(String path, ChildListener listener);

    protected abstract List<String> addTargetChildListener(String path, TargetChildListener listener);

    protected abstract void removeTargetChildListener(String path, TargetChildListener listener);

    /**
     * we invoke the zookeeper client to delete the node
     * @param path the node path
     */
    protected abstract void deletePath(String path);
}
