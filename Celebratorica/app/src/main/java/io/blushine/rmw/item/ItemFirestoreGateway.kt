package io.blushine.rmw.item

import android.content.res.Resources
import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import io.blushine.android.common.ObjectEvent
import io.blushine.android.firebase.FirebaseAuth
import io.blushine.rmw.R
import io.blushine.rmw.util.AppActivity
import io.blushine.utils.EventBus


private val TAG = ItemFirestoreGateway::class.simpleName ?: "INVALID"

/**
 * Firestore gateway for [Item] and [Category]
 */
internal class ItemFirestoreGateway : ItemGateway {
	private val resources: Resources = AppActivity.getActivity()!!.resources
	private val eventBus = EventBus.getInstance()

	private fun db(): FirebaseFirestore {
		return FirebaseFirestore.getInstance()
	}

	override fun addCategory(category: Category) {
		category.userId = getUserId()
		val newDoc = getCategory()
		category.id = newDoc.id
		newDoc.set(category)
				.addOnSuccessListener {
					eventBus.post(CategoryEvent(ObjectEvent.Actions.ADDED, category))
				}
				.addOnFailureListener {
					eventBus.post(CategoryEvent(ObjectEvent.Actions.ADD_FAILED, category))
				}
	}

	private fun getUserId(): String {
		val user = FirebaseAuth.getCurrentUser()
		return if (user != null) {
			user.uid
		} else {
			Log.w(TAG, "getUserId() — No user logged in")
			""
		}
	}

	/**
	 * Get the specified category
	 * @param id category id, if not specified creates a new Category document
	 */
	private fun getCategory(id: String? = null): DocumentReference {
		return if (id != null) {
			db().document(id)
		} else {
			db().collection(resources.getString(R.string.table_category)).document()
		}
	}

	/**
	 * Get the specified item
	 * @param id item id, if not specified creates a new Item document
	 */
	private fun getItem(id: String? = null): DocumentReference {
		return if (id != null) {
			db().document(id)
		} else {
			db().collection(resources.getString(R.string.table_item)).document()
		}
	}

	override fun getCategories() {
		val categoryCollection = db().collection(resources.getString(R.string.table_category))

		categoryCollection
				.whereEqualTo("userId", getUserId())
				.orderBy("order")
				.get()
				.addOnCompleteListener { task ->
					if (task.isSuccessful) {
						val categories = ArrayList<Category>()
						for (categoryDocument in task.result.documents) {
							val category = categoryDocument.toObject(Category::class.java)
							if (category != null) {
								categories.add(category)
							}
						}
						eventBus.post(CategoryEvent(ObjectEvent.Actions.GET_RESPONSE, categories))
					} else {
						eventBus.post(CategoryEvent(ObjectEvent.Actions.GET_FAILED))
					}
				}
	}

	override fun updateCategories(categories: List<Category>) {
		db()
				.runTransaction { transaction ->
					for (category in categories) {
						val doc = getCategory(category.id)
						transaction.set(doc, category)
					}
				}
				.addOnSuccessListener {
					eventBus.post(CategoryEvent(ObjectEvent.Actions.EDITED, categories))
				}
				.addOnFailureListener {
					eventBus.post(CategoryEvent(ObjectEvent.Actions.EDIT_FAILED))
				}
	}

	override fun removeCategory(category: Category) {
		removeCategoryGetItems(category)
	}

	private fun removeCategoryGetItems(category: Category) {
		// Get all items from this category
		val itemCollection = db().collection(resources.getString(R.string.table_item))

		itemCollection
				.whereEqualTo("userId", getUserId())
				.get()
				.addOnCompleteListener { task ->
					if (task.isSuccessful) {
						val items = ArrayList<DocumentReference>()
						for (itemDocument in task.result.documents) {
							items.add(itemDocument.reference)
						}
						removeCategoryGetCategories(category, items)
					} else {
						eventBus.post(CategoryEvent(ObjectEvent.Actions.REMOVE_FAILED, category))
					}
				}
	}

	private fun removeCategoryGetCategories(category: Category, itemsToRemove: List<DocumentReference>) {
		val categoryCollection = db().collection(resources.getString(R.string.table_category))

		categoryCollection
				.whereEqualTo("userId", getUserId())
				.whereGreaterThan("order", category.order)
				.get()
				.addOnCompleteListener { task ->
					if (task.isSuccessful) {
						val categoriesToUpdate = ArrayList<Pair<DocumentReference, Category>>()
						for (categoryDocument in task.result.documents) {
							val currentCategory = categoryDocument.toObject(Category::class.java)
							if (currentCategory != null) {
								currentCategory.order -= 1
								categoriesToUpdate.add(Pair(categoryDocument.reference, currentCategory))
							}
						}

						removeCategory(category, itemsToRemove, categoriesToUpdate)
					} else {
						eventBus.post(CategoryEvent(ObjectEvent.Actions.REMOVE_FAILED, category))
					}
				}
	}

	private fun removeCategory(category: Category, itemsToRemove: List<DocumentReference>, categoriesToUpdate: List<Pair<DocumentReference, Category>>) {
		db().runTransaction { transaction ->
			// Delete category
			transaction.delete(getCategory(category.id))

			// Delete items
			for (itemReference in itemsToRemove) {
				transaction.delete(itemReference)
			}

			// Update category order
			for (categoryPair in categoriesToUpdate) {
				transaction.set(categoryPair.first, categoryPair.second)
			}
		}
				.addOnSuccessListener {
					eventBus.post(CategoryEvent(ObjectEvent.Actions.REMOVED, category))
				}
				.addOnFailureListener {
					eventBus.post(CategoryEvent(ObjectEvent.Actions.REMOVE_FAILED, category))
				}
	}

	override fun addItems(items: List<Item>) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun updateItems(items: List<Item>) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun removeItems(items: List<Item>) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getItems(categoryId: String?) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun importData(categories: List<Category>, items: List<Item>) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}
}