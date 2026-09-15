package com.kazumaproject.markdownhelperkeyboard.snippet.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSnippetBinding
import com.kazumaproject.markdownhelperkeyboard.snippet.database.Snippet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * スニペット（ワンタップで入力するテキスト）の管理画面。
 * 追加はカード、編集はダイアログ、並び替えはハンドルのドラッグで行う。
 */
@AndroidEntryPoint
class SnippetFragment : Fragment() {

    private val viewModel: SnippetViewModel by viewModels()

    private var _binding: FragmentSnippetBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SnippetAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    /** ドラッグ中は DB からの再配信で並びが戻らないよう submitList を止める。 */
    private var isDragging = false

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { viewModel.exportJson() }
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter()
                        .use { it.write(json) }
                }
            }.onSuccess { toast(getString(R.string.snippet_export_success)) }
                .onFailure(::showError)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)!!.bufferedReader()
                        .use { it.readText() }
                }
                withContext(Dispatchers.IO) { viewModel.importJson(json) }
            }.onSuccess { count -> toast(getString(R.string.snippet_import_success, count)) }
                .onFailure(::showError)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSnippetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.user_template_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.action_export -> {
                    if (viewModel.snippets.value.isEmpty()) {
                        toast(getString(R.string.snippet_nothing_to_export))
                    } else {
                        exportLauncher.launch("snippet_backup.json")
                    }
                    true
                }

                R.id.action_import -> {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                    true
                }

                R.id.action_delete_all -> {
                    showDeleteAllConfirmationDialog()
                    true
                }

                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        adapter = SnippetAdapter(
            onClick = ::showEditDialog,
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) },
        )
        binding.recyclerViewSnippets.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = false

            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) isDragging = true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                isDragging = false
                val orderedIds = adapter.currentList.map { it.id }
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { viewModel.reorder(orderedIds) }.onFailure(::showError)
                }
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewSnippets)
    }

    private fun setupListeners() {
        binding.fabAddSnippet.setOnClickListener {
            val showCard = binding.cardViewAddSnippet.isGone
            binding.cardViewAddSnippet.isGone = !showCard
            if (showCard) {
                binding.layoutSnippetExplanation.isGone = true
                binding.fabAddSnippet.setImageResource(com.kazumaproject.core.R.drawable.remove)
                binding.editTextSnippetText.requestFocus()
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.editTextSnippetText, InputMethodManager.SHOW_IMPLICIT)
            } else {
                binding.fabAddSnippet.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            }
        }

        binding.buttonAddSnippet.setOnClickListener { addSnippet() }

        binding.buttonToggleSnippetExplanation.setOnClickListener {
            binding.layoutSnippetExplanation.isGone = !binding.layoutSnippetExplanation.isGone
            if (!binding.layoutSnippetExplanation.isGone) {
                binding.cardViewAddSnippet.isGone = true
                binding.fabAddSnippet.setImageResource(android.R.drawable.ic_input_add)
                hideKeyboardAndClearFocus()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snippets.collect { list ->
                    if (!isDragging) adapter.submitList(list)
                    // 最初の 1 件を登録するまでは説明を見せる
                    if (list.isEmpty() && binding.cardViewAddSnippet.isGone) {
                        binding.layoutSnippetExplanation.isGone = false
                    }
                }
            }
        }
    }

    private fun addSnippet() {
        val label = binding.editTextSnippetLabel.text?.toString().orEmpty().trim()
        val text = binding.editTextSnippetText.text?.toString().orEmpty()
        if (text.isBlank()) {
            toast(getString(R.string.snippet_empty_text_error))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { viewModel.save(Snippet(label = label, text = text, sortOrder = 0)) }
                .onSuccess {
                    toast(getString(R.string.snippet_saved))
                    binding.editTextSnippetLabel.text?.clear()
                    binding.editTextSnippetText.text?.clear()
                    binding.cardViewAddSnippet.isGone = true
                    binding.fabAddSnippet.setImageResource(android.R.drawable.ic_input_add)
                    hideKeyboardAndClearFocus()
                }
                .onFailure(::showError)
        }
    }

    private fun showEditDialog(snippet: Snippet) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_snippet, null)
        val editLabel = dialogView.findViewById<EditText>(R.id.edit_text_snippet_label_dialog)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text_snippet_text_dialog)
        editLabel.setText(snippet.label)
        editText.setText(snippet.text)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.snippet_edit))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_string)) { _, _ ->
                val newLabel = editLabel.text.toString().trim()
                val newText = editText.text.toString()
                if (newText.isBlank()) {
                    toast(getString(R.string.snippet_empty_text_error))
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { viewModel.save(snippet.copy(label = newLabel, text = newText)) }
                        .onSuccess { toast(getString(R.string.updated_string)) }
                        .onFailure(::showError)
                }
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .setNeutralButton(getString(R.string.delete_string)) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.confirm_delete_title))
                    .setMessage(getString(R.string.snippet_delete_confirm, snippet.displayName()))
                    .setPositiveButton(getString(R.string.delete_string)) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { viewModel.delete(snippet.id) }
                                .onSuccess { toast(getString(R.string.snippet_deleted)) }
                                .onFailure(::showError)
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel_string), null)
                    .show()
            }
            .show()
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_all_delete_title))
            .setMessage(getString(R.string.snippet_delete_all_confirm))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { viewModel.deleteAll() }.onFailure(::showError)
                }
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .show()
    }

    private fun Snippet.displayName(): String =
        label.ifBlank { text.lineSequence().firstOrNull().orEmpty().take(30) }

    private fun hideKeyboardAndClearFocus() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    private fun showError(throwable: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.snippet_operation_failed, throwable.message.orEmpty()),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun toast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
