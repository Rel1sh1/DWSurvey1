/**
 * Copyright (c) 2005-2011 springside.org.cn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * $Id: SimpleHibernateDao.java 1594 2011-05-11 14:22:29Z calvinxiu $
 */
package net.diaowen.common.dao;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.diaowen.common.utils.AssertUtils;
import net.diaowen.common.utils.ReflectionUtils;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.metadata.ClassMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * 封装Hibernate原生API的DAO泛型基类.
 *
 * 参考Spring2.5自带的Petlinc例子, 取消了HibernateTemplate, 直接使用Hibernate原生API.
 *
 * @param <T> DAO操作的对象类型
 * @param <ID> 主键类型
 *
 */
public class SimpleHibernateDao<T, ID extends Serializable> implements ISimpleHibernateDao<T, ID> {

	protected Logger logger = LoggerFactory.getLogger(getClass());

	protected SessionFactory sessionFactory;

	protected Class<T> entityClass;


	/**
	 * 通过子类的泛型定义取得对象类型Class.
	 * public class UserDao extends SimpleHibernateDao<User, Long>
	 */
	public SimpleHibernateDao() {
		this.entityClass = ReflectionUtils.getSuperClassGenricType(getClass());
	}

	/**
	 * 设置实体类型
	 * @param entityClass
	 */
	public SimpleHibernateDao(Class<T> entityClass) {
		this.entityClass = entityClass;
	}
	/**
	 * 取得sessionFactory.
	 *
	 * @return SessionFactory
	 */
	@Override
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	/**
	 * 采用@Autowired按类型注入SessionFactory, 当有多个SesionFactory的时候在子类重载本函数.
	 *
	 * @param sessionFactory sessionFactory
	 */
	@Override
	@Autowired
	public void setSessionFactory(final SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * 取得当前Session.
	 *
	 * @return session
	 */
	@Override
	public Session getSession() {
		return sessionFactory.getCurrentSession();
	}

	/**
	 * 保存新增或修改的对象.
	 *
	 * @param entity 对象必须是session中的对象或含id属性的transient对象
	 */
	@Override
	public void save(final T entity) {
		try {
			AssertUtils.notNull(entity, "entity不能为空");
			getSession().saveOrUpdate(entity);
			logger.debug("save entity: {}", entity);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	/**
	 * 删除对象.
	 *
	 * @param entity 对象必须是session中的对象或含id属性的transient对象.
	 */
	@Override
	public void delete(final T entity) {
		AssertUtils.notNull(entity, "entity不能为空");
		getSession().delete(entity);
		logger.debug("delete entity: {}", entity);
	}

	/**
	 * 按id删除对象.
	 *
	 * @param id 对象id
	 */
	@Override
	public void delete(final ID id) {
		AssertUtils.notNull(id, "id不能为空");
		delete(get(id));
		logger.debug("delete entity {},id is {}", entityClass.getSimpleName(), id);
	}

	/**
	 * 按id获取对象.
	 *
	 * @param id 对象id
	 * @return 对象
	 */
	@Override
	public T get(final ID id) {
		AssertUtils.notNull(id, "id不能为空");
		return (T) getSession().load(entityClass, id);
	}

	/**
	 * 按id列表获取对象列表.
	 *
	 * @param  ids id列表
	 * @return List<T>
	 */
	@Override
	public List<T> get(final Collection<ID> ids) {
		return find(Restrictions.in(getIdName(), ids));
	}

	/**
	 *	获取全部对象.
	 *
	 * @return List<T>
	 */
	@Override
	public List<T> getAll() {
		return find();
	}

	/**
	 *	获取全部对象, 支持按属性行序.
	 *
	 * @param orderByProperty 属性
	 * @param isAsc 是否升序
	 * @return List<T>
	 */
	@Override
	public List<T> getAll(String orderByProperty, boolean isAsc) {
		Criteria c = createCriteria();
		if (isAsc) {
			c.addOrder(Order.asc(orderByProperty));
		} else {
			c.addOrder(Order.desc(orderByProperty));
		}
		return c.list();
	}

	/**
	 * 按属性查找对象列表, 匹配方式为相等.
	 *
	 * @param propertyName 属性名
	 * @param value 值
	 * @return List<T>
	 */
	@Override
	public List<T> findBy(final String propertyName, final Object value) {
		AssertUtils.hasText(propertyName, "propertyName不能为空");
		Criterion criterion = Restrictions.eq(propertyName, value);
		return find(criterion);
	}

	/**
	 * 按属性查找唯一对象, 匹配方式为相等.
	 *
	 * @param propertyName 属性名
	 * @param value 值
	 * @return T 对象
	 */
	@Override
	public T findUniqueBy(final String propertyName, final Object value) {
		AssertUtils.hasText(propertyName, "propertyName不能为空");
		Criterion criterion = Restrictions.eq(propertyName, value);
		return (T) createCriteria(criterion).uniqueResult();
	}

	/**
	 * 按HQL查询对象列表.
	 *
	 * @param values 数量可变的参数,按顺序绑定.
	 */
	@Override
	public <X> List<X> find(final String hql, final Object... values) {
		return createQuery(hql, values).list();
	}

	/**
	 * 按HQL查询对象列表.
	 *
	 * @param values 命名参数,按名称绑定.
	 */
	@Override
	public <X> List<X> find(final String hql, final Map<String, ?> values) {
		return createQuery(hql, values).list();
	}

	/**
	 * 按HQL查询唯一对象.
	 *
	 * @param values 数量可变的参数,按顺序绑定.
	 */
	@Override
	public <X> X findUnique(final String hql, final Object... values) {
		return (X) createQuery(hql, values).uniqueResult();
	}

	/**
	 * 按HQL查询唯一对象.
	 *
	 * @param values 命名参数,按名称绑定.
	 */
	@Override
	public <X> X findUnique(final String hql, final Map<String, ?> values) {
		return (X) createQuery(hql, values).uniqueResult();
	}

	/**
	 * 执行HQL进行批量修改/删除操作.
	 *
	 * @param values 数量可变的参数,按顺序绑定.
	 * @return 更新记录数.
	 */
	@Override
	public int batchExecute(final String hql, final Object... values) {
		return createQuery(hql, values).executeUpdate();
	}

	/**
	 * 执行HQL进行批量修改/删除操作.
	 *
	 * @param values 命名参数,按名称绑定.
	 * @return 更新记录数.
	 */
	@Override
	public int batchExecute(final String hql, final Map<String, ?> values) {
		return createQuery(hql, values).executeUpdate();
	}
	/**
	 * 根据查询HQL与参数列表创建Query对象.
	 * 与find()函数可进行更加灵活的操作.
	 *
	 * @param values 数量可变的参数,按顺序绑定.
	 */
	@Override
	public Query createQuery(final String queryString, final Object... values) {
		AssertUtils.hasText(queryString, "queryString不能为空");
		Query query = getSession().createQuery(queryString);
		if (values != null) {
			for (int i = 1; i <= values.length; i++) {
				query.setParameter(i, values[i-1]);
			}
		}
		return query;
	}

	/**
	 * 根据查询HQL与参数列表创建Query对象.
	 * 与find()函数可进行更加灵活的操作.
	 *
	 * @param values 命名参数,按名称绑定.
	 */
	@Override
	public Query createQuery(final String queryString, final Map<String, ?> values) {
		AssertUtils.hasText(queryString, "queryString不能为空");
		Query query = getSession().createQuery(queryString);
		if (values != null) {
			query.setProperties(values);
		}
		return query;
	}

	/**
	 * 按Criteria查询对象列表.
	 *
	 * @param criterions 数量可变的Criterion.
	 */
	@Override
	public List<T> find(final Criterion... criterions) {
		return createCriteria(criterions).list();
	}

	/**
	 * 按Criteria查询唯一对象.
	 *
	 * @param criterions 数量可变的Criterion.
	 */
	@Override
	public T findUnique(final Criterion... criterions) {
		return (T) createCriteria(criterions).uniqueResult();
	}

	/**
	 * 根据Criterion条件创建Criteria.
	 * 与find()函数可进行更加灵活的操作.
	 *
	 * @param criterions 数量可变的Criterion.
	 */
	@Override
	public Criteria createCriteria(final Criterion... criterions) {
		Criteria criteria = getSession().createCriteria(entityClass);
		for (Criterion c : criterions) {
			criteria.add(c);
		}
		return criteria;
	}

	/**
	 * @param criterions
	 * @return criteria
	 */
	@Override
	public Criteria createCriteria(List<Criterion> criterions) {
		Criteria criteria = getSession().createCriteria(entityClass);
		for (Criterion c : criterions) {
			criteria.add(c);
		}
		return criteria;
	}

	/**
	 * 初始化对象.
	 * 使用load()方法得到的仅是对象Proxy, 在传到View层前需要进行初始化.
	 * 如果传入entity, 则只初始化entity的直接属性,但不会初始化延迟加载的关联集合和属性.
	 * 如需初始化关联属性,需执行:
	 * Hibernate.initialize(user.getRoles())，初始化User的直接属性和关联集合.
	 * Hibernate.initialize(user.getDescription())，初始化User的直接属性和延迟加载的Description属性.
	 */
	@Override
	public void initProxyObject(Object proxy) {
		Hibernate.initialize(proxy);
	}

	/**
	 * Flush当前Session.
	 */
	@Override
	public void flush() {
		getSession().flush();
	}

	/**
	 * 为Query添加distinct transformer.
	 * 预加载关联对象的HQL会引起主对象重复, 需要进行distinct处理.
	 *
	 */
	@Override
	public Query distinct(Query query) {
		query.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		return query;
	}

	/**
	 * 为Criteria添加distinct transformer.
	 * 预加载关联对象的HQL会引起主对象重复, 需要进行distinct处理.
	 *
	 */
	@Override
	public Criteria distinct(Criteria criteria) {
		criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		return criteria;
	}

	/**
	 * 取得对象的主键名.
	 *
	 * @return Name 名字
	 */
	@Override
	public String getIdName() {
		ClassMetadata meta = getSessionFactory().getClassMetadata(entityClass);
		return meta.getIdentifierPropertyName();
	}

	/**
	 * 判断对象的属性值在数据库内是否唯一.
	 *
	 * 在修改对象的情景下,如果属性新修改的值(value)等于属性原来的值(orgValue)则不作比较.
	 * @param propertyName 属性名
	 * @param newValue 新值
	 * @param oldValue 旧值
	 * @return 布尔值
	 */
	@Override
	public boolean isPropertyUnique(final String propertyName, final Object newValue, final Object oldValue) {
		if (newValue == null || newValue.equals(oldValue)) {
			return true;
		}
		Object object = findUniqueBy(propertyName, newValue);
		return (object == null);
	}
}
