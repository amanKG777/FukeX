-- Links in the Markdown files are relative to the repository root; the pages live in docs/.
local repo = "https://github.com/amanKG777/FukeX/"

function Link(el)
  local t = el.target
  if t == "documentation.md" then
    el.target = "documentation.html"
  elseif t == "../../releases/latest" then
    el.target = repo .. "releases/latest"
  elseif t:match("^[%w_/%-]+%.md$") or t == "LICENSE" or t:match("^[%w_%-]+/") then
    if not t:match("^https?:") and not t:match("^#") then
      el.target = repo .. "blob/master/" .. t
    end
  end
  return el
end
