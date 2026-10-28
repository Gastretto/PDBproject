-- Pandoc filter for the PDF build.
-- Single Pandoc pass so the metadata (repo root) is read before images are rewritten.

function Pandoc(doc)
  local root = nil
  local meta = doc.meta["pdfbuild-root"]
  if meta ~= nil then
    root = pandoc.utils.stringify(meta)
  end

  local function rewrite(img)
    local src = tostring(img.src)
    local diagram = src:match("([^/]+)%.svg$")
    if diagram and root then
      img.src = root .. "/.pdfbuild/" .. diagram .. ".png"
      -- Wide, shallow diagrams should span the text column instead of
      -- being scaled down to their natural width.
      if diagram == "navrh-2" then
        img.classes = {"wide"}
      end
    elseif src:match("^[%w%-_]+%.png$") and root then
      img.src = root .. "/" .. src
    end
    return img
  end

  -- The first level-1 heading of the document is its title.
  local title_done = false

  doc.blocks = doc.blocks:walk({
    Image = rewrite,
    Header = function(el)
      if el.level == 1 and not title_done then
        title_done = true
        el.classes = {"title"}
      end
      return el
    end,
  })

  return doc
end
